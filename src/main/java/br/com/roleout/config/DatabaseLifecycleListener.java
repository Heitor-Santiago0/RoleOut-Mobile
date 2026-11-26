package br.com.roleout.config;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public final class DatabaseLifecycleListener implements ServletContextListener {
    private java.util.concurrent.ScheduledExecutorService pushWorker;
    private java.util.concurrent.ScheduledExecutorService recoveryWorker;
    @Override public void contextInitialized(ServletContextEvent event) {
        var recovery=new br.com.roleout.service.PasswordRecoveryService();
        if(recovery.enabled()){
            recoveryWorker=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r->{var thread=new Thread(r,"roleout-recovery");thread.setDaemon(true);return thread;});
            recoveryWorker.scheduleWithFixedDelay(()->{
                try{recovery.dispatch();}catch(Exception e){event.getServletContext().log("Recovery worker unavailable; will retry.");}
            },10,15,java.util.concurrent.TimeUnit.SECONDS);
        }
        if (!br.com.roleout.service.PushService.enabled()) return;
        var push = new br.com.roleout.service.PushService();
        pushWorker = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r,"roleout-push"); thread.setDaemon(true); return thread;
        });
        pushWorker.scheduleWithFixedDelay(() -> {
            try { push.collect(); push.dispatch(); }
            catch (Exception e) { event.getServletContext().log("Schedule notifications unavailable; will retry. " + e.getClass().getSimpleName()); }
        }, 15, 60, java.util.concurrent.TimeUnit.SECONDS);
    }
    @Override
    public void contextDestroyed(ServletContextEvent event) {
        if(recoveryWorker!=null){
            recoveryWorker.shutdownNow();
            try{recoveryWorker.awaitTermination(15,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        }
        if (pushWorker != null) {
            pushWorker.shutdownNow();
            try { pushWorker.awaitTermination(15,java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        DatabaseManager.getInstance().close();
    }
}
