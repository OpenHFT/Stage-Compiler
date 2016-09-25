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
