package net.openhft.sg;

import java.util.Set;

@Staged
class TypeParamExtensionChainsSub<K> extends TypeParamExtensionChainsSup<Set<K>> {
    final Object bla = finalInitInCtr;
    
    TypeParamExtensionChainsSub() {}
    
}
