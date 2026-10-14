package tw.stan.homequiet;
import android.content.*;
public class BootReceiver extends BroadcastReceiver { @Override public void onReceive(Context c, Intent i){ Geo.register(c); } }
