package dev.shirokai.daily.core;
/** Monotonic, connection-local clock; dimension changes never create an extra session. */
public final class Activity {
    public record Sample(long total,long eligible){}
    private long sampled,active; private double x,y,z; private String dimension;
    public Activity(long now,double x,double y,double z,String dimension){sampled=active=now;this.x=x;this.y=y;this.z=z;this.dimension=dimension;}
    public void interaction(long now){active=now;}
    public Sample sample(long now,double nx,double ny,double nz,String world,boolean afk,int threshold){
        if(!dimension.equals(world)){x=nx;y=ny;z=nz;dimension=world;}
        else if((nx-x)*(nx-x)+(ny-y)*(ny-y)+(nz-z)*(nz-z)>=0.25){x=nx;y=ny;z=nz;active=now;}
        long total=Math.max(0,(now-sampled)/1_000_000_000L);
        long eligible=afk?Math.max(0,Math.min(total,(active+threshold*1_000_000_000L-sampled)/1_000_000_000L)):total;
        sampled+=total*1_000_000_000L;return new Sample(total,eligible);
    }
}
