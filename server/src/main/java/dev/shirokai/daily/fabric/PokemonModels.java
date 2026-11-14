package dev.shirokai.daily.fabric;

import net.minecraft.item.ItemStack;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;

/** Optional Cobblemon linkage: keep menus usable when its model API is unavailable. */
final class PokemonModels {
    private static boolean warned;
    private PokemonModels() {}
    static ItemStack create(String identifier){
        try {
            Class<?> registry=Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonSpecies");
            Object instance=registry.getField("INSTANCE").get(null);
            for(Object species:(Iterable<?>)registry.getMethod("getSpecies").invoke(instance)){
                if(!identifier.equals(species.getClass().getMethod("getResourceIdentifier").invoke(species).toString()))continue;
                // Official PokemonItem.from(Species, Set<String>, int, Vector4f?) factory.
                for(Method factory:Class.forName("com.cobblemon.mod.common.item.PokemonItem").getMethods()){
                    Class<?>[] args=factory.getParameterTypes();
                    if(factory.getName().equals("from")&&Modifier.isStatic(factory.getModifiers())&&args.length==4
                            &&args[0].isInstance(species)&&args[1]==Set.class&&args[2]==int.class&&args[3].getName().equals("org.joml.Vector4f"))
                        return (ItemStack)factory.invoke(null,species,Set.of(),1,null);
                }
                throw new NoSuchMethodException("PokemonItem.from(Species, Set, int, Vector4f)");
            }
        }catch(ReflectiveOperationException|LinkageError|RuntimeException error){
            if(!warned){warned=true;ShirokaiDaily.LOG.warn("Modelo de Pokémon indisponível; usando ícone de bússola nas hunts",error);}
        }
        return ItemStack.EMPTY;
    }
}
