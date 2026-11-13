package dev.shirokai.daily.fabric;
import net.minecraft.server.network.ServerPlayerEntity;
import java.lang.reflect.*;
final class Permissions {
    private static volatile dev.shirokai.daily.core.Config.General settings;
    static void configure(dev.shirokai.daily.core.Config.General value){settings=value;}
    static boolean has(ServerPlayerEntity player,String node){
        var config=settings;
        String actual=config==null?node:config.permissions().getOrDefault(node,node);
        try {
            if(config!=null&&!config.integrations().getOrDefault("luckperms",true))return fallback(player,node);
            Object api=Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get").invoke(null);
            Object manager=Class.forName("net.luckperms.api.LuckPerms").getMethod("getUserManager").invoke(api);
            Object user=Class.forName("net.luckperms.api.model.user.UserManager").getMethod("getUser",java.util.UUID.class).invoke(manager,player.getUuid());
            if(user==null)return false;
            Object cached=Class.forName("net.luckperms.api.model.PermissionHolder").getMethod("getCachedData").invoke(user);
            Object permission=Class.forName("net.luckperms.api.cacheddata.CachedDataManager").getMethod("getPermissionData").invoke(cached);
            Object result=Class.forName("net.luckperms.api.cacheddata.CachedPermissionData").getMethod("checkPermission",String.class).invoke(permission,actual);
            return (boolean)result.getClass().getMethod("asBoolean").invoke(result);
        }catch(ClassNotFoundException missing){return fallback(player,node);}
        catch(ReflectiveOperationException failure){return false;}
    }
    private static boolean fallback(ServerPlayerEntity player,String node){
        return node.equals("shirokaidaily.admin")?player.hasPermissionLevel(2):java.util.Set.of("shirokaidaily.daily","shirokaidaily.playtime","shirokaidaily.hunts").contains(node);
    }
}
