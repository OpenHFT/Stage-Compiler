package net.openhft.sg;

import spoon.reflect.cu.CompilationUnit;
import spoon.reflect.cu.SourcePosition;

import java.io.File;

public class LinedSourcePosition implements SourcePosition {
    SourcePosition delegate;
    int line;

    public LinedSourcePosition(SourcePosition delegate, int line) {
        this.delegate = delegate;
        this.line = line;
    }

    @Override
    public File getFile() {
        return delegate.getFile();
    }

    @Override
    public CompilationUnit getCompilationUnit() {
        return delegate != null ? delegate.getCompilationUnit() : null;
    }

    @Override
    public int getLine() {
        return line;
    }

    @Override
    public int getEndLine() {
        return line + (delegate != null ?
                delegate.getEndLine() - delegate.getLine() : 0);
    }

    @Override
    public int getColumn() {
        return delegate != null ? delegate.getColumn() : 0;
    }

    @Override
    public int getEndColumn() {
        return delegate.getEndColumn();
    }

    @Override
    public int getSourceEnd() {
        return delegate.getSourceEnd();
    }

    @Override
    public int getSourceStart() {
        return delegate.getSourceStart();
    }
}
