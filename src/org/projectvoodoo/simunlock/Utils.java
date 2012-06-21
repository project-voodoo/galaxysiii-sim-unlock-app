
package org.projectvoodoo.simunlock;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import android.os.Build;

public class Utils {

    static boolean canGetRootPermission() {
        String check = "SuCheck";
        String command = "echo " + check;
        try {
            ArrayList<String> output = run("su", command);
            if (output.get(0).equals(check))
                return true;
        } catch (Exception e) {
        }

        return false;
    }

    static ArrayList<String> run(String shell, String command) throws IOException {
        ArrayList<String> output = new ArrayList<String>();

        Process process = Runtime.getRuntime().exec(shell);

        BufferedOutputStream shellInput =
                new BufferedOutputStream(process.getOutputStream());
        BufferedReader shellOutput =
                new BufferedReader(new InputStreamReader(process.getInputStream()));

        shellInput.write((command + "\n").getBytes());

        shellInput.write("exit\n".getBytes());
        shellInput.flush();

        String line;
        while ((line = shellOutput.readLine()) != null)
            output.add(line);

        try {
            process.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (process.exitValue() != 0)
            throw new IOException();

        return output;
    }

    public static boolean isCompatibleDevice() {
        for (String deviceNamePattern : App.VALID_BUILD_MODEL)
            if (Build.MODEL.matches(deviceNamePattern))
                return true;

        return false;
    }

}
