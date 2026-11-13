package dev.shirokai.daily.core;
import dev.shirokai.daily.core.integration.ImpactorEconomy;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class ImpactorEconomyTest {
 static class Api implements ImpactorEconomy.Access {
  boolean registered=true,failDeposit;String currency;int deposits,saves;
  CompletableFuture<Object> account=CompletableFuture.completedFuture(new Object());
  CompletableFuture<Void> persisted=new CompletableFuture<>();
  public Optional<Object> currency(String id){currency=id;return registered?Optional.of(new Object()):Optional.empty();}
  public CompletionStage<Object> account(Object currency,UUID owner){return account;}
  public CompletionStage<Boolean> deposit(Object account,int amount){deposits++;return failDeposit?CompletableFuture.failedFuture(new RuntimeException("connection lost")):CompletableFuture.completedFuture(true);}
  public CompletionStage<Void> save(Object account){saves++;return persisted;}
 }
 @Test void successWaitsForDurableSave(){Api api=new Api();var result=new ImpactorEconomy(api,Runnable::run).deposit(UUID.randomUUID(),"impactor:dollars",10).toCompletableFuture();assertFalse(result.isDone());assertEquals(1,api.deposits);assertEquals(1,api.saves);api.persisted.complete(null);assertEquals("DONE",result.join());assertEquals("impactor:dollars",api.currency);}
 @Test void noCurrencyNeverFallsBackToPrimary(){Api api=new Api();api.registered=false;assertEquals("PENDING",new ImpactorEconomy(api,Runnable::run).deposit(UUID.randomUUID(),"impactor:tokens",10).toCompletableFuture().join());assertEquals(0,api.deposits);assertEquals("impactor:tokens",api.currency);}
 @Test void depositFailureIsUncertainAndNotRetried(){Api api=new Api();api.failDeposit=true;assertEquals("UNCERTAIN",new ImpactorEconomy(api,Runnable::run).deposit(UUID.randomUUID(),"impactor:dollars",10).toCompletableFuture().join());assertEquals(1,api.deposits);assertEquals(0,api.saves);}
 @Test void saveFailureDoesNotDepositAgain(){Api api=new Api();var result=new ImpactorEconomy(api,Runnable::run).deposit(UUID.randomUUID(),"impactor:dollars",10).toCompletableFuture();api.persisted.completeExceptionally(new RuntimeException("disk failed"));assertEquals("UNCERTAIN",result.join());assertEquals(1,api.deposits);}
 @Test void depositIsMarshalledToGameExecutor(){Api api=new Api();var queued=new ArrayDeque<Runnable>();var result=new ImpactorEconomy(api,queued::add).deposit(UUID.randomUUID(),"impactor:tokens",10).toCompletableFuture();assertEquals(0,api.deposits);assertFalse(result.isDone());queued.removeFirst().run();assertEquals(1,api.deposits);api.persisted.complete(null);assertEquals("DONE",result.join());}
 @Test void arbitraryCurrencyNeverAccepted(){Api api=new Api();assertEquals("PENDING",new ImpactorEconomy(api,Runnable::run).deposit(UUID.randomUUID(),"impactor:primary",10).toCompletableFuture().join());assertEquals(0,api.deposits);}
}
