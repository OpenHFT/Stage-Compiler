package net.openhft.sg;

import java.util.function.Consumer;

@Staged
class TypeParamExtensionChainsSup<E> {
    final Object finalInitInCtr;
    TypeParamExtensionChainsSup() {
        finalInitInCtr = null;
    }
    
    void foo(E e) {
        Object foo = null;
        E bar = (E) foo;
    }
    void bar(Consumer<? super E> c) {}
}
