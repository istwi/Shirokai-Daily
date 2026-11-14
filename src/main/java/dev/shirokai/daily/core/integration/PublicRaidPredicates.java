package dev.shirokai.daily.core.integration;
import java.lang.reflect.*;
import java.util.function.Predicate;
/** Optional linkage to explicit PUBLIC Raid APIs. Never resolve methods on an actor implementation. */
public final class PublicRaidPredicates<A> implements Predicate<A> {
 private final Method boss,capture;
 public PublicRaidPredicates(Class<A> actorApi,Class<?> combatApi,Class<?> raidApi)throws ReflectiveOperationException{
  boss=method(combatApi,"isBossActor",actorApi);capture=method(raidApi,"isCaptureActor",actorApi);
 }
 private static Method method(Class<?> api,String name,Class<?> actor)throws ReflectiveOperationException{
  Method m=api.getMethod(name,actor);
  if(!Modifier.isPublic(api.getModifiers())||!Modifier.isPublic(m.getModifiers())||!Modifier.isStatic(m.getModifiers())||m.getReturnType()!=boolean.class)throw new NoSuchMethodException("Unsupported public Raid API: "+name);
  return m;
 }
 @Override public boolean test(A actor){
  try{return (boolean)boss.invoke(null,actor)||(boolean)capture.invoke(null,actor);}
  catch(ReflectiveOperationException e){throw new IllegalStateException("Public Shirokai Raids predicate failed",e);}
 }
}
