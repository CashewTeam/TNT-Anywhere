#!/data/data/com.termux/files/usr/bin/sh
export CLASSPATH=`pwd`/安卓屏连-x11.apk
chmod -w $CLASSPATH
unset LD_LIBRARY_PATH LD_PRELOAD
exec /system/bin/app_process -Xnoimage-dex2oat / com.termux.x11.Loader "$@"
