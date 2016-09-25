/*
 * Copyright (C) 2015,2016  higherfrequencytrading.com
 * Copyright (C) 2016 Roman Leventov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.openhft.sg;

import spoon.reflect.code.CtCodeSnippetExpression;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtThisAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static java.lang.Math.min;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static net.openhft.sg.CompilationContext.namedHashedMap;
import static net.openhft.sg.ExtensionChains.add;
import static net.openhft.sg.StageGraphCompilationException.sgce;
import static spoon.reflect.declaration.ModifierKind.*;

public class CompilationNode {
    
    public static CompilationNode root(Factory f) {
        return new CompilationNode(f);
    }
    
    final Factory f;
    final CompilationContext cxt;
    final CompilationNode parent;
    List<CtClass<?>> classesToMerge = new ArrayList<>();
    private List<CompilationNode> innerNodes = new ArrayList<>();
    boolean eraseTypeParameters = false;
    
    CtField<?> parentAccessField;
    private List<CtField<?>> rootAccessPath;
    
    private CtClass<?> mergedClass;

    Map<CtMethod<?>, MethodNode> interfaceMethodToNode = namedHashedMap();
    
    private CompilationNode(Factory f) {
        this.f = f;
        this.cxt = new CompilationContext();
        this.parent = null;
    }
    
    private CompilationNode(CompilationNode parent) {
        this.f = parent.f;
        this.cxt = parent.cxt;
        this.parent = parent;
        parent.innerNodes.add(this);
    }
    
    public CompilationNode createChild() {
        return new CompilationNode(this);
    }
    
    public CompilationNode eraseTypeParameters() {
        eraseTypeParameters = true;
        return this;
    }
    
    public CompilationNode addClassToMerge(Class<?> classToMerge) {
        if (classToMerge.getAnnotation(Staged.class) == null)
            throw sgce("All compiled classes should be annotated Staged: " + classToMerge);
        CtClass<Object> ctClass = f.Class().get(classToMerge);
        classesToMerge.add(ctClass);
        cxt.bind(ctClass, this);
        Compiler.stagedClassExtensionChain(ctClass)
                .forEach(c -> cxt.bindAnyStagedClassToNode(c, this));
        return this;
    }

    public CompilationNode addClassToMerge(CtClass<?> classToMerge) {
        if (classToMerge.getAnnotation(Staged.class) == null)
            throw sgce("All compiled classes should be annotated Staged: " + classToMerge);
        classesToMerge.add(classToMerge);
        cxt.bind(classToMerge, this);
        Compiler.stagedClassExtensionChain(classToMerge)
                .forEach(c -> cxt.bindAnyStagedClassToNode(c, this));
        return this;
    }

    void computeRootAccessPath() {
        if (parent == null) {
            rootAccessPath = emptyList();
        } else {
            if (parentAccessField == null)
                throw sgce("Parent compilation node is not referenced: " + classesToMerge);
            rootAccessPath = new ArrayList<>(parent.rootAccessPath);
            rootAccessPath.add(parentAccessField);
        }
        innerNodes.forEach(CompilationNode::computeRootAccessPath);
    }
    
    List<CtField<?>> accessPath(CompilationNode target) {
        int equalUntil = 0;
        int maxLen = min(rootAccessPath.size(), target.rootAccessPath.size());
        while (equalUntil < maxLen &&
                rootAccessPath.get(equalUntil) == target.rootAccessPath.get(equalUntil)) {
            equalUntil++;
        }
        return target.rootAccessPath.subList(equalUntil,target.rootAccessPath.size());
    }

    CtExpression<?> access(CompilationNode target, AccessType accessType) {
        List<CtField<?>> accessPath = accessPath(target);
        CompilationNode thisNodeToAccess = this;
        while (!target.rootAccessPath.subList(0,
                min(thisNodeToAccess.rootAccessPath.size(), target.rootAccessPath.size()))
                .equals(thisNodeToAccess.rootAccessPath)) {
            thisNodeToAccess = thisNodeToAccess.parent;
        }
        CtFieldAccess<?> access = FieldAccessChains.chainToAccess(accessPath, accessType);
        CtClass<?> classToMerge = thisNodeToAccess.classesToMerge.get(0);
        CtThisAccess<?> thisAccess = f.Code().createThisAccess(classToMerge.getReference());
//        thisAccess.setImplicit(false);
        if (access != null) {
            CtFieldAccess<?> innerMostAccess = access;
            while (innerMostAccess.getTarget() != null) {
                innerMostAccess = (CtFieldAccess<?>) innerMostAccess.getTarget();
            }
            innerMostAccess.setTarget(thisAccess);
            return access;
        } else {
            return thisAccess;
        }
    }

    void bind(CtMethod<?> method, MethodNode node) {
        if (interfaceMethodToNode.putIfAbsent(method, node) != null &&
                interfaceMethodToNode.get(method) != node) {
            throw new StageGraphCompilationException("compilation node already has node " +
                    interfaceMethodToNode.get(method) + " bind to abstract method " + method +
                    " in interface " + method.getDeclaringType() + "; attempt to bind to " + node);
        }
    }
    
    CtClass<?> getMergedClass() {
        if (mergedClass != null)
            return mergedClass;
        if (classesToMerge.size() == 1) {
            mergedClass = classesToMerge.get(0);
            finalProcessMergedClass();
            return mergedClass;
        }
        String name = classesToMerge.stream().map(CtClass::getSimpleName).collect(joining("_"));
        mergedClass = f.Class().create(classesToMerge.get(0).getPackage(), name);
        mergedClass.setModifiers(EnumSet.of(PUBLIC));
        classesToMerge.forEach(ctClass -> {
            ctClass.getFormalTypeParameters().stream()
                    .filter(p -> !mergedClass.getFormalTypeParameters().contains(p)).
                    forEach(mergedClass::addFormalTypeParameter);
            ctClass.getSuperInterfaces().forEach(mergedClass::addSuperInterface);
            ctClass.getAnonymousExecutables()
                    .forEach(ae -> add(mergedClass, ae, mergedClass::addAnonymousExecutable));
            ctClass.getNestedTypes().forEach(t -> add(mergedClass, t, mergedClass::addNestedType));
            ctClass.getFields().forEach(f -> add(mergedClass, f, mergedClass::addField));
            ctClass.getConstructors()
                   .stream()
                   .filter(c -> !c.isImplicit())
                   .forEach((CtConstructor c) -> add(mergedClass, c, mergedClass::addConstructor));
            if (ctClass.getSuperclass() != null)
                mergedClass.setSuperclass(ctClass.getSuperclass());

            ctClass.getMethods().forEach(m -> add(mergedClass, m, mergedClass::addMethod));
        });
        finalProcessMergedClass();
        return mergedClass;
    }
    
    private void finalProcessMergedClass() {
        if (eraseTypeParameters) {
            mergedClass.setFormalTypeParameters(emptyList());
        }
        
        if (!mergedClass.getModifiers().isEmpty()) {
            mergedClass.setModifiers(EnumSet.copyOf(mergedClass.getModifiers()));
            mergedClass.removeModifier(ABSTRACT);
        }
    }
    
    void mergeChildNodes() {
        CtClass<?> mergedClass = getMergedClass();
        for (CompilationNode child : innerNodes) {
            CtClass<?> childMergedClass = child.getMergedClass();
            add(mergedClass, childMergedClass, mergedClass::addNestedType);
            CtCodeSnippetExpression constructor = f.Core().createCodeSnippetExpression();
            constructor.setValue("new " + childMergedClass.getSimpleName() + "()");
            child.parentAccessField.setDefaultExpression(constructor);
            child.parentAccessField.setType((CtTypeReference) childMergedClass.getReference());
            child.parentAccessField.addModifier(FINAL);
            child.mergeChildNodes();
        }
    }
}
