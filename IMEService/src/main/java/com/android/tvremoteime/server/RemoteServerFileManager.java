package com.android.tvremoteime.server;

/**
 * Created by kingt on 2018/1/10.
 */
import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.android.tvremoteime.IMEService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import fi.iki.elonen.*;
import xllib.DownloadManager;
import xllib.DownloadTask;

public class RemoteServerFileManager implements NanoHTTPD.TempFileManager {
    static File baseDir = new File(Environment.getExternalStorageDirectory(), "tvremoteime");
    private static File filesDir = new File(baseDir, "files");
    private static File tmpDataDir = new File(baseDir, "temp");
    private static File playerCacheDir = new File(baseDir, "xlplayer");
    public static void resetBaseDir(Context context){
        baseDir = context.getExternalFilesDir(null);
        filesDir = new File(baseDir, "files");
        tmpDataDir = new File(baseDir, "temp");
        playerCacheDir = new File(baseDir, "xlplayer");
    }
    public static class SDCardTempFile implements NanoHTTPD.TempFile {
        private final File file;
        private OutputStream fstream;
        private boolean isTemp;
        public SDCardTempFile(String fileName) throws IOException {
            if(fileName == null || fileName.isEmpty()){
                this.file = File.createTempFile("tmp-", "", tmpDataDir);
                isTemp = true;
            }else {
                this.file = new File(filesDir, fileName);
                isTemp = false;
            }
            try {
                if (file.exists()) file.delete();
            }catch (Exception ex){}
        }

        public void delete() throws Exception {
            if(this.fstream != null) this.fstream.close();
            //上传的文件不用删除
            if(this.isTemp && !this.file.delete()) {
                Log.e(IMEService.TAG, String.format("无法删除临时文件[%s]", this.getName()));
            }
        }

        public String getName() {
            return this.file.getAbsolutePath();
        }

        public OutputStream open() throws Exception {
            if(this.fstream == null) this.fstream = new FileOutputStream(this.file);
            return this.fstream;
        }
    }

    private final List<NanoHTTPD.TempFile> tempFiles;
    private RemoteServerFileManager() {
        this.tempFiles = new ArrayList<NanoHTTPD.TempFile>();
    }
    @Override
    public void clear() {
        if (!this.tempFiles.isEmpty()) {
            for (NanoHTTPD.TempFile file : this.tempFiles) {
                try {
                    file.delete();
                } catch (Exception ignored) {
                    Log.e(IMEService.TAG, String.format("删除临时文件[%s]时出错.", file.getName()), ignored);
                }
            }
            this.tempFiles.clear();
        }
    }

    @Override
    public NanoHTTPD.TempFile createTempFile(String fileName) throws Exception {
        if(fileName != null && !fileName.isEmpty()) {
            fileName = fileName.replaceAll("[\\\\|/]", "").replaceAll("\\.\\.", "");
        }
        NanoHTTPD.TempFile tmpFile = new SDCardTempFile(fileName);
        tempFiles.add(tmpFile);
        return tmpFile;
    }

    public static class Factory implements NanoHTTPD.TempFileManagerFactory {
        @Override
        public NanoHTTPD.TempFileManager create() {
            try{
                if(!filesDir.exists())filesDir.mkdirs();
                if(!tmpDataDir.exists())tmpDataDir.mkdirs();
                DownloadTask.setBaseDirGetter(new DownloadTask.DownloadTaskBaseDirGetter() {
                    @Override
                    public File getBaseDir() {
                        return playerCacheDir;
                    }
                });
            }catch (Exception ex){}
            return new RemoteServerFileManager();
        }
    }

    public static void clearAllFiles(){
        try{
            if(filesDir.exists()) deleteDirFiles(filesDir);
            if(tmpDataDir.exists()) deleteDirFiles(filesDir);
            if(playerCacheDir != null && playerCacheDir.exists()) deleteDirFiles(playerCacheDir);
        }catch (Exception ex){

        }
    }
    public static void deleteDirFiles(File file){
        File[] files =  file.listFiles();
        for (File f : files) {
            try {
                if(f.isDirectory())deleteDirFiles(f);
                f.delete();
            } catch (SecurityException e) {
            }
        }
    }
    public static void deleteFile(File file){
        if(file.exists()) {
            if (file.isDirectory()) {
                deleteDirFiles(file);
            }
            try {
                file.delete();
            } catch (SecurityException e) {
            }
        }
    }
    public static void cutFile(File sourceFile, File targetPath){
        if(sourceFile.exists()) {
            File targetFile = new File(targetPath, sourceFile.getName());
            sourceFile.renameTo(targetFile);
            /**
            if (sourceFile.isDirectory()) {
                File newDir = new File(targetPath, sourceFile.getName());
                if(newDir.mkdir()){
                    File[] files =  sourceFile.listFiles();
                    for (File f : files) {
                        cutFile(f, newDir);
                    }
                    deleteFile(sourceFile);
                }
            } else {
                File targetFile = new File(targetPath, sourceFile.getName());
                sourceFile.renameTo(targetFile);
            }
             **/
        }
    }
    public static void copyFile(File sourceFile, File targetPath){
        if(sourceFile.exists()) {
            if (sourceFile.isDirectory()) {
                File newDir = new File(targetPath, sourceFile.getName());
                if(newDir.mkdir()){
                    File[] files =  sourceFile.listFiles();
                    for (File f : files) {
                        copyFile(f, newDir);
                    }
                }
            } else {
                File targetFile = new File(targetPath, sourceFile.getName());
                copyFileImpl(sourceFile, targetFile);
            }
        }
    }
    private static void copyFileImpl(File sourceFile, File targetFile){
        if(targetFile.exists()) return;
        try {
            FileInputStream ins = new FileInputStream(sourceFile);
            FileOutputStream out = new FileOutputStream(targetFile);
            byte[] b = new byte[1024];
            int n = 0;
            while ((n = ins.read(b)) != -1) {
                out.write(b, 0, n);
            }
            ins.close();
            out.close();
        }catch (Exception e) {
        }
    }
}
