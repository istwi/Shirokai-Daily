package dev.shirokai.daily.core.integration;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

/** Impactor 5.3.5 API, inspected from the supplied Fabric JAR. No Minecraft or implementation internals. */
public final class ImpactorEconomy {
    public interface Access {
        Optional<Object> currency(String id)throws Exception;
        CompletionStage<Object> account(Object currency,UUID owner)throws Exception;
        CompletionStage<Boolean> deposit(Object account,int amount)throws Exception;
        CompletionStage<Void> save(Object account)throws Exception;
    }
    private final Access api;private final Executor game;
    public ImpactorEconomy(Access api,Executor game){this.api=api;this.game=game;}
    public static ImpactorEconomy fromRuntime(Executor game)throws ReflectiveOperationException{return new ImpactorEconomy(new ReflectiveAccess(),game);}
    public CompletionStage<String> deposit(UUID owner,String currency,int amount){
        if(!Set.of("impactor:dollars","impactor:tokens").contains(currency)||amount<1)return CompletableFuture.completedFuture("PENDING");
        try {
            Optional<Object> resolved=api.currency(currency);if(resolved.isEmpty())return CompletableFuture.completedFuture("PENDING");
            return api.account(resolved.get(),owner).thenComposeAsync(account->{
                try {
                    // depositAsync in 5.3.5 can execute synchronously. Always invoke it on the game executor.
                    return api.deposit(account,amount).thenCompose(result->{
                        if(!result)return CompletableFuture.completedFuture("UNCERTAIN");
                        try{return api.save(account).thenApply(ignored->"DONE");}
                        catch(Exception failure){return CompletableFuture.completedFuture("UNCERTAIN");}
                    }).exceptionally(failure->"UNCERTAIN");
                }catch(Exception failure){return CompletableFuture.completedFuture("UNCERTAIN");}
            },game).exceptionally(failure->"UNCERTAIN");
        }catch(Exception unavailable){return CompletableFuture.completedFuture("PENDING");}
    }
    private static final class ReflectiveAccess implements Access {
        final Class<?> serviceType,currencyType,accountType,transactionType,keyType,providerType;
        final Object economy;
        ReflectiveAccess()throws ReflectiveOperationException{
            serviceType=Class.forName("net.impactdev.impactor.api.economy.EconomyService");
            currencyType=Class.forName("net.impactdev.impactor.api.economy.currency.Currency");
            providerType=Class.forName("net.impactdev.impactor.api.economy.currency.CurrencyProvider");
            accountType=Class.forName("net.impactdev.impactor.api.economy.accounts.Account");
            transactionType=Class.forName("net.impactdev.impactor.api.economy.transactions.EconomyTransaction");
            keyType=Class.forName("net.kyori.adventure.key.Key");
            // Validate the complete public ABI before starting any operation.
            serviceType.getMethod("account",currencyType,UUID.class);serviceType.getMethod("save",accountType);
            accountType.getMethod("depositAsync",BigDecimal.class);transactionType.getMethod("successful");
            economy=serviceType.getMethod("instance").invoke(null);
        }
        @SuppressWarnings("unchecked") public Optional<Object> currency(String id)throws Exception{
            Object provider=serviceType.getMethod("currencies").invoke(economy);
            Object key=keyType.getMethod("key",String.class).invoke(null,id);
            return (Optional<Object>)providerType.getMethod("currency",keyType).invoke(provider,key);
        }
        @SuppressWarnings("unchecked") public CompletionStage<Object> account(Object currency,UUID owner)throws Exception{
            return (CompletionStage<Object>)serviceType.getMethod("account",currencyType,UUID.class).invoke(economy,currency,owner);
        }
        @SuppressWarnings("unchecked") public CompletionStage<Boolean> deposit(Object account,int amount)throws Exception{
            var stage=(CompletionStage<Object>)accountType.getMethod("depositAsync",BigDecimal.class).invoke(account,BigDecimal.valueOf(amount));
            return stage.thenApply(report->{try{return (boolean)transactionType.getMethod("successful").invoke(report);}catch(ReflectiveOperationException e){throw new CompletionException(e);}});
        }
        @SuppressWarnings("unchecked") public CompletionStage<Void> save(Object account)throws Exception{return (CompletionStage<Void>)serviceType.getMethod("save",accountType).invoke(economy,account);}
    }
}
