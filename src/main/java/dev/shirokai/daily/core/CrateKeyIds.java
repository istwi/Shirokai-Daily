package dev.shirokai.daily.core;
import java.util.Set;
/** Compatibility for the confirmed old Basic reward ID, including persisted deliveries. */
public final class CrateKeyIds {
    private CrateKeyIds() {}
    public static String resolve(String requested,Set<?> available){
        if(available.contains(requested))return requested;
        if(requested.equals("inicial_rara")&&available.contains("caixa_inicial_rara"))return "caixa_inicial_rara";
        return null;
    }
}
