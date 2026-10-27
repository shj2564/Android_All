package kr.song.peugeot.fmbridge;

import android.content.Context;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;
import java.lang.reflect.Method;

final class ReglinkRadioController {
    private static final String TAG="CMZX-FM";
    private static final String DROID="com.reglink.services.IDroidService";
    private static final String RADIO="com.reglink.services.IRadioService";
    private static final String HUB="com.reglink.services.IMediaHubService";
    private static final String MEDIA="com.reglink.services.IMediaService";
    private final Context context;
    ReglinkRadioController(Context c){context=c.getApplicationContext();}

    private IBinder sm(String name){
        try{
            Class<?> x=Class.forName("android.os.ServiceManager");
            Method m=x.getDeclaredMethod("getService",String.class);
            m.setAccessible(true);
            return (IBinder)m.invoke(null,name);
        }catch(Throwable t){Log.e(TAG,"ServiceManager "+name,t);return null;}
    }

    private IBinder svc(String name){
        IBinder d=sm("reglink.droid"); if(d==null)return null;
        Parcel a=Parcel.obtain(),r=Parcel.obtain();
        try{
            a.writeInterfaceToken(DROID);a.writeString(name);
            if(!d.transact(2,a,r,0))return null;
            r.readException();return r.readStrongBinder();
        }catch(Throwable t){Log.e(TAG,"svc "+name,t);return null;}
        finally{r.recycle();a.recycle();}
    }

    private boolean radioVoid(int code){
        IBinder b=svc("Radio");if(b==null)return false;
        Parcel a=Parcel.obtain(),r=Parcel.obtain();
        try{
            a.writeInterfaceToken(RADIO);
            if(!b.transact(code,a,r,0))return false;
            r.readException();return true;
        }catch(Throwable t){Log.e(TAG,"radio tx "+code,t);return false;}
        finally{r.recycle();a.recycle();}
    }

    boolean power(boolean on){
        IBinder b=svc("Radio");if(b==null)return false;
        Parcel a=Parcel.obtain(),r=Parcel.obtain();
        try{
            a.writeInterfaceToken(RADIO);a.writeInt(on?1:0);
            if(!b.transact(3,a,r,0))return false;
            r.readException();return true;
        }catch(Throwable t){Log.e(TAG,"power",t);return false;}
        finally{r.recycle();a.recycle();}
    }

    int getFreq(){
        IBinder b=svc("Radio");if(b==null)return 0;
        Parcel a=Parcel.obtain(),r=Parcel.obtain();
        try{
            a.writeInterfaceToken(RADIO);
            if(!b.transact(7,a,r,0))return 0;
            r.readException();return r.readInt();
        }catch(Throwable t){Log.e(TAG,"getFreq",t);return 0;}
        finally{r.recycle();a.recycle();}
    }

    boolean tune(int freq){
        activate();
        IBinder b=svc("Radio");if(b==null)return false;
        Parcel a=Parcel.obtain(),r=Parcel.obtain();
        try{
            a.writeInterfaceToken(RADIO);a.writeString("fm");a.writeInt(freq);
            if(!b.transact(6,a,r,0))return false;
            r.readException();return true;
        }catch(Throwable t){Log.e(TAG,"tune",t);return false;}
        finally{r.recycle();a.recycle();}
    }

    boolean seek(boolean up){activate();return radioVoid(up?10:11);}
    boolean step(boolean up){activate();return radioVoid(up?8:9);}
    boolean scan(boolean start){activate();return radioVoid(start?12:13);}

    void activate(){activeClient();switchAudio();power(true);}

    private boolean activeClient(){
        IBinder b=svc("MediaHub");if(b==null)return false;
        Parcel a=Parcel.obtain(),r=Parcel.obtain();
        try{
            a.writeInterfaceToken(HUB);a.writeString("radio");
            if(!b.transact(3,a,r,0))return false;
            r.readException();return r.readInt()!=0;
        }catch(Throwable t){Log.w(TAG,"activeClient",t);return false;}
        finally{r.recycle();a.recycle();}
    }

    private boolean switchAudio(){
        try{
            Context rc=context.createPackageContext("com.reglink.apps.radio",
                    Context.CONTEXT_INCLUDE_CODE|Context.CONTEXT_IGNORE_SECURITY);
            Class<?> c=rc.getClassLoader().loadClass("com.reglink.parcel.AudioSource");
            Object source=c.getMethod("makeAudioSource",int.class,int.class).invoke(null,1,2);
            IBinder b=svc("Media");if(b==null)return false;
            Parcel a=Parcel.obtain(),r=Parcel.obtain();
            try{
                a.writeInterfaceToken(MEDIA);a.writeInt(1);
                source.getClass().getMethod("writeToParcel",Parcel.class,int.class).invoke(source,a,0);
                if(!b.transact(1,a,r,0))return false;
                r.readException();return r.readInt()!=0;
            }finally{r.recycle();a.recycle();}
        }catch(Throwable t){Log.w(TAG,"switchAudio",t);return false;}
    }
}
