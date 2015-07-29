package net.openhft.sg;

import com.sun.source.util.Trees;
import spoon.Launcher;
import spoon.compiler.Environment;
import spoon.reflect.code.CtThisAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.DefaultJavaPrettyPrinter;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;


public class ContextProcessor extends AbstractProcessor {

    private Trees trees;

    public ContextProcessor() {
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_8;
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        trees = Trees.instance(processingEnv);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(Context.class.getName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends TypeElement> contextClasses =
                (Set<? extends TypeElement>) roundEnv.getElementsAnnotatedWith(Context.class);
        String[] classPathStrings = getClassPathStrings();
        contextClasses.forEach(cxtC -> {
            Launcher spoon = new Launcher();
            spoon.getEnvironment().setComplianceLevel(8);
            spoon.getEnvironment().setNoClasspath(true);

            spoon.getEnvironment().setSourceClasspath(classPathStrings);

            AnnotationMirror am = getAnnotationMirror(cxtC, Context.class);
            Stream.concat(Stream.of(trees.getPath(cxtC)),
            Stream.concat(getAnnotationValue(am, "topLevel").stream(),
                    getAnnotationValue(am, "nested").stream())
                    .map(typeMirror -> trees.getPath(asTypeElement(typeMirror)))
            ).map(p -> {
                String path = Paths.get(p.getCompilationUnit().getSourceFile().toUri())
                        .toAbsolutePath().toString();
                String[] parts = path.split("java");
                assert parts.length >= 2;
                return Arrays.asList(parts).subList(0, parts.length - 1).stream()
                        .collect(joining("java", "", "java"));
            }).distinct().forEach(spoon::addInputResource);
            spoon.buildModel();

            Factory factory = spoon.getFactory();
            factory.getEnvironment().setAutoImports(true);

            try {
                String mergedClassName = "Compiled" + cxtC.getSimpleName();
                JavaFileObject sourceFile =
                        processingEnv.getFiler().createSourceFile(mergedClassName, cxtC);
                if (Files.exists(Paths.get(sourceFile.toUri())))
                    return;

                CompilationNode root = CompilationNode.root(factory);
                root.addClassToMerge(factory.Class().get(cxtC.getQualifiedName().toString()));

                AnnotationMirror cxtAnn = getAnnotationMirror(cxtC, Context.class);
                for (TypeMirror topLevelClassMirror : getAnnotationValue(cxtAnn, "topLevel")) {
                    CtClass<Object> topLevelClass = factory.Class().get(
                            asTypeElement(topLevelClassMirror).getQualifiedName().toString());
                    root.addClassToMerge(topLevelClass);
                }

                for (TypeMirror nestedClassMirror : getAnnotationValue(cxtAnn, "nested")) {
                    CtClass<Object> nestedClass = factory.Class().get(
                            asTypeElement(nestedClassMirror).getQualifiedName().toString());
                    root.createChild().addClassToMerge(nestedClass).eraseTypeParameters();
                }

                Compiler compiler = new Compiler(root);

                compiler.setMergedClassName(mergedClassName);
                CtClass<?> resultClass = compiler.compile();
                MyPrinter printer = new MyPrinter(factory.getEnvironment());
                Collection<CtTypeReference<?>> imports = printer.computeImports(resultClass);
                printer.writeHeader(singletonList(resultClass), imports);
                printer.scan(resultClass);

                try (Writer writer = sourceFile.openWriter()) {
                    writer.write(printer.getResult());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        return false;
    }

    private String[] getClassPathStrings() {
        ClassLoader cl = getClass().getClassLoader();
        URL[] currentClassPath = ((URLClassLoader) cl).getURLs();
        return Arrays.stream(currentClassPath).map(url -> {
            try {
                return Paths.get(url.toURI()).toAbsolutePath();
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }).distinct().filter(Files::exists).map(Path::toString).toArray(String[]::new);
    }

    private static AnnotationMirror getAnnotationMirror(TypeElement typeElement, Class<?> clazz) {
        String clazzName = clazz.getName();
        for(AnnotationMirror m : typeElement.getAnnotationMirrors()) {
            if(m.getAnnotationType().toString().equals(clazzName)) {
                return m;
            }
        }
        return null;
    }

    private static List<TypeMirror> getAnnotationValue(
            AnnotationMirror annotationMirror, String key) {
        for(Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                annotationMirror.getElementValues().entrySet() ) {
            if(entry.getKey().getSimpleName().toString().equals(key)) {
                return ((List<AnnotationValue>) entry.getValue().getValue())
                        .stream().map(av -> (TypeMirror) av.getValue()).collect(toList());
            }
        }
        throw new AssertionError();
    }

    private TypeElement asTypeElement(TypeMirror typeMirror) {
        Types typeUtils = this.processingEnv.getTypeUtils();
        return (TypeElement) typeUtils.asElement(typeMirror);
    }

    class MyPrinter extends DefaultJavaPrettyPrinter {

        Deque<CtClass<?>> currentThis = new ArrayDeque<>();

        public MyPrinter(Environment env) {
            super(env);
        }

        @Override
        public <T> void visitCtClass(CtClass<T> ctClass) {
            currentThis.push(ctClass);
            super.visitCtClass(ctClass);
            currentThis.pop();
        }

        @Override
        public <T> void visitCtThisAccess(CtThisAccess<T> thisAccess) {
            enterCtExpression(thisAccess);
            if (thisAccess.getTarget() != null && thisAccess.isImplicit()) {
                throw new RuntimeException("inconsistent this definition");
            }
            if (thisAccess.getType().getDeclaration() != currentThis.peek()) {
                visitCtTypeReferenceWithoutGenerics(thisAccess.getType());
                write(".");
            }
            if (!thisAccess.isImplicit()) {
                write("this");
            }
            exitCtExpression(thisAccess);
        }
    }
}
