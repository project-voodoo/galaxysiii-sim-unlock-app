
package org.projectvoodoo.simunlock;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.os.Environment;
import android.util.Log;

public class UnlockTools {

    private static final String TAG = "Voodoo SIMUnlock Utils";

    private static final String NV_DATA_BIN_FILE = "/efs/nv_data.bin";
    private static final String NV_DATA_BIN_MD5_FILE = "/efs/nv_data.bin.md5";

    private static final int LOCK_STATUS_OFFSET = 0x181469;

    /*
     * Temp files
     */
    private static String getModifiedFileName() {
        return App.context.getCacheDir() + "/nvdatabin_copy_modified";

    }

    private static String getWorkingCopyFileName() {
        return App.context.getCacheDir() + "/nvdatabin_copy_original";
    }

    private static void copyNvDataBinWorkingCopy() throws IOException {
        copyFileAsRoot(NV_DATA_BIN_FILE, getWorkingCopyFileName());
    }

    static void copyModifiedNvDataBin() throws IOException {
        copyFileAsRoot(getModifiedFileName(), NV_DATA_BIN_FILE);
    }

    /*
     * Efs key data backup utility
     */
    static boolean backupNvDataBin() throws IOException {
        boolean ret = false;
        String state = Environment.getExternalStorageState();

        if (Environment.MEDIA_MOUNTED.equals(state)) {
            if (new File("/sdcard/").getFreeSpace() < 4 * 1024 * 1024) // 4MB
                return false;

            long time = System.currentTimeMillis();
            copyFileAsRoot(
                    NV_DATA_BIN_FILE,
                    "/sdcard/voodoo_sim_unlock_" + time + "_nv_data.bin");

            copyFileAsRoot(
                    NV_DATA_BIN_MD5_FILE,
                    "/sdcard/voodoo_sim_unlock_" + time + "_nv_data.bin.md5");
        }

        return ret;
    }

    /*
     * Routines for reading and modifying Baseband data
     */
    static void modifyNvDataBinCopy() throws IOException {
        copyNvDataBinWorkingCopy();

        FileInputStream fis = new FileInputStream(getWorkingCopyFileName());
        byte[] buffer = new byte[fis.available()];
        fis.read(buffer);
        Log.i(TAG, "Curent value: " + buffer[LOCK_STATUS_OFFSET]);

        buffer[LOCK_STATUS_OFFSET] = 0;
        FileOutputStream outFos = new FileOutputStream(getModifiedFileName());
        outFos.write(buffer);
        outFos.close();

        UnlockTools.copyModifiedNvDataBin();
    }

    static int readNvDataBin() throws IOException {
        copyNvDataBinWorkingCopy();

        FileInputStream fis = new FileInputStream(getWorkingCopyFileName());
        byte[] buffer = new byte[fis.available()];
        fis.read(buffer);

        for (int i = LOCK_STATUS_OFFSET - 1024; i < LOCK_STATUS_OFFSET; i++)
            if (buffer[i] != (byte) 0xFF)
                return -1;

        return (int) buffer[LOCK_STATUS_OFFSET];
    }

    static void writeNewNvDataBinMd5() throws IOException {
        String newMd5 = findNvDataBinMd5();

        if (newMd5 == null)
            return;

        Utils.run("su", "echo " + newMd5 + " > " + NV_DATA_BIN_MD5_FILE);
    }

    private static String findNvDataBinMd5() throws IOException {
        String rildMd5 = null;

        String pattern = "MD5 fail.*md5 '[a-z0-9]{32}' " +
                "computed md5 '([a-z0-9]{32})'.*";
        Pattern p = Pattern.compile(pattern);

        for (String s : Utils.run("su", "cat /efs/nv.log")) {
            Matcher m = p.matcher(s);
            if (m.find())
                rildMd5 = m.group(1);
        }

        if (rildMd5 != null)
            Log.i(TAG, "rild md5: " + rildMd5);

        return rildMd5;
    }

    /*
     * General tools
     */
    private static int getProcessPid(String cmdline) {
        int pid = -1;
        Pattern p = Pattern.compile("[0-9]*");
        char[] buf = cmdline.toCharArray();

        for (File procFile : new File("/proc/").listFiles())
            if (procFile.isDirectory()) {
                Matcher m = p.matcher(procFile.getName());
                if (m.find())
                    try {
                        File cmdFile = new File(procFile.getAbsolutePath() + "/cmdline");
                        if (!cmdFile.isFile())
                            continue;

                        FileReader reader = new FileReader(cmdFile);
                        int len = reader.read(buf);
                        if (len == buf.length && Arrays.equals(buf, cmdline.toCharArray())) {

                            try {
                                pid = Integer.parseInt(procFile.getName());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    } catch (Exception e) {
                    }
            }

        return pid;
    }

    static void killRild() throws IOException {
        int pid = getProcessPid("/system/bin/rild");

        if (pid > 0)
            Utils.run("su", "kill " + pid);
    }

    private static void copyFileAsRoot(String source, String destination) throws IOException {
        Utils.run("su", "cat " + source + " > " + destination);
    }

}
