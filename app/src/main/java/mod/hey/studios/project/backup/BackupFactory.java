package mod.hey.studios.project.backup;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.besome.sketch.beans.BlockBean;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import a.a.a.lC;
import a.a.a.yB;
import mod.hey.studios.editor.manage.block.ExtraBlockInfo;
import mod.hey.studios.editor.manage.block.v2.BlockLoader;
import mod.hey.studios.project.custom_blocks.CustomBlocksManager;
import mod.hey.studios.util.Helper;
import mod.hilal.saif.activities.tools.ConfigActivity;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

public class BackupFactory {
    public static final String EXTENSION = "swb";
    public static final String DEF_PATH = ".sketchware/backups/";

    private static final String[] resSubfolders = {
            "fonts", "icons", "images", "sounds"
    };

    final String sc_id;
    File outPath;
    boolean backupLocalLibs;
    boolean backupCustomBlocks;
    String error = "";
    boolean restoreSuccess = true;

    /**
     * @param sc_id For backing up, the target project's ID,
     *              for restoring, the new project ID
     */
    public BackupFactory(String sc_id) {
        this.sc_id = sc_id;
    }

    public static String getBackupDir() {
        return new File(Environment.getExternalStorageDirectory(), ConfigActivity.getBackupPath())
                .getAbsolutePath();
    }

    private static File getAllLocalLibsDir() {
        return new File(Environment.getExternalStorageDirectory(),
                ".sketchware/libs/local_libs");
    }

    private static HashMap<String, Object> getProject(File file) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            byte[] key = "sketchwaresecure".getBytes();
            cipher.init(2, new SecretKeySpec(key, "AES"), new IvParameterSpec(key));
            byte[] encrypted;
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                encrypted = new byte[(int) raf.length()];
                raf.readFully(encrypted);
            }
            byte[] decrypted = cipher.doFinal(encrypted);
            String decryptedString = new String(decrypted);

            return new Gson().fromJson(decryptedString.trim(), Helper.TYPE_MAP);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean writeEncrypted(File file, String string) {
        String path = file.getAbsolutePath();

        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            byte[] key = "sketchwaresecure".getBytes();
            cipher.init(1, new SecretKeySpec(key, "AES"), new IvParameterSpec(key));
            byte[] encrypted = cipher.doFinal((string.trim()).getBytes());
            try (RandomAccessFile raf = new RandomAccessFile(path, "rw")) {
                raf.setLength(0);
                raf.write(encrypted);
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String getNewScId() {
        File myscList = new File(Environment.getExternalStorageDirectory(),
                ".sketchware/mysc/list/");

        ArrayList<String> list = new ArrayList<>();
        FileUtil.listDir(myscList.getAbsolutePath(), list);
        //noinspection Java8ListSort
        Collections.sort(list, String.CASE_INSENSITIVE_ORDER);

        int id = list.isEmpty() ? 600 : Integer.parseInt(new File(list.get(list.size() - 1)).getName());
        return String.valueOf(id + 1);
    }

    /************************ UTILITIES ************************/

    public static boolean unzip(File zipFile, File destinationDir) {
        int DEFAULT_BUFFER = 2048;
        try (ZipFile zip = new ZipFile(zipFile)) {
            destinationDir.mkdirs();
            Enumeration<? extends ZipEntry> zipFileEntries = zip.entries();
            while (zipFileEntries.hasMoreElements()) {
                ZipEntry entry = zipFileEntries.nextElement();
                String entryName = entry.getName();
                File destFile = new File(destinationDir, entryName);
                File destinationParent = destFile.getParentFile();
                if (destinationParent != null && !destinationParent.exists()) {
                    destinationParent.mkdirs();
                }
                if (!entry.isDirectory()) {
                    try (BufferedInputStream is = new BufferedInputStream(zip.getInputStream(entry))) {
                        int currentByte;
                        byte[] data = new byte[DEFAULT_BUFFER];
                        try (FileOutputStream fos = new FileOutputStream(destFile)) {
                            try (BufferedOutputStream dest = new BufferedOutputStream(fos, DEFAULT_BUFFER)) {
                                while ((currentByte = is.read(data, 0, DEFAULT_BUFFER)) != -1 /*EOF*/) {
                                    dest.write(data, 0, currentByte);
                                }
                                dest.flush();
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    private void parseManifest(File manifestFile, String sc_id) throws Exception {
        ArrayList<String> permissions = new ArrayList<>();
        ArrayList<String> services = new ArrayList<>();
        ArrayList<String> receivers = new ArrayList<>();

        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new FileInputStream(manifestFile), "UTF-8");

        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                String tagName = parser.getName();
                if (tagName.equals("uses-permission")) {
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        if (parser.getAttributeName(i).equals("name")) {
                            permissions.add(parser.getAttributeValue(i));
                            break;
                        }
                    }
                } else if (tagName.equals("service")) {
                     for (int i = 0; i < parser.getAttributeCount(); i++) {
                        if (parser.getAttributeName(i).equals("name")) {
                            services.add(parser.getAttributeValue(i));
                            break;
                        }
                    }
                } else if (tagName.equals("receiver")) {
                     for (int i = 0; i < parser.getAttributeCount(); i++) {
                        if (parser.getAttributeName(i).equals("name")) {
                            receivers.add(parser.getAttributeValue(i));
                            break;
                        }
                    }
                }
            }
            eventType = parser.next();
        }

        FileUtil.writeFile(new File(getPermissionsPath(sc_id)).getAbsolutePath(), new Gson().toJson(permissions));
        FileUtil.writeFile(new File(getServicesPath(sc_id)).getAbsolutePath(), String.join("\n", services));
        FileUtil.writeFile(new File(getReceiversPath(sc_id)).getAbsolutePath(), String.join("\n", receivers));
    }

    private String getAppNameFromManifest(File manifestFile) throws Exception {
        String manifestContent = FileUtil.readFile(manifestFile.getAbsolutePath());
        Pattern pattern = Pattern.compile("android:label=\"@string/([^\"]+)\"");
        Matcher matcher = pattern.matcher(manifestContent);
        if (matcher.find()) {
            String appNameKey = matcher.group(1);
            File stringsXml = new File(manifestFile.getParentFile(), "res/values/strings.xml");
            if (stringsXml.exists()) {
                String stringsContent = FileUtil.readFile(stringsXml.getAbsolutePath());
                Pattern stringsPattern = Pattern.compile("<string name=\"" + appNameKey + "\">([^<]+)</string>");
                Matcher stringsMatcher = stringsPattern.matcher(stringsContent);
                if (stringsMatcher.find()) {
                    return stringsMatcher.group(1);
                }
            }
        } else {
            pattern = Pattern.compile("android:label=\"([^\"]+)\"");
            matcher = pattern.matcher(manifestContent);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "Imported Project";
    }

    private File getJavaFilesPath() {
        return new File(getDataDir(), "files/java");
    }

    private File getResourcesPath() {
        return new File(getDataDir(), "files/resource");
    }

    private File getAssetsPath() {
        return new File(getDataDir(), "files/assets");
    }

    private File getNativeLibsPath() {
        return new File(getDataDir(), "files/native_libs");
    }

    private String getPermissionsPath(String sc_id) {
        return new File(Environment.getExternalStorageDirectory(), ".sketchware/data/" + sc_id + "/permission").getAbsolutePath();
    }

    private String getServicesPath(String sc_id) {
        return new File(Environment.getExternalStorageDirectory(), ".sketchware/data/" + sc_id + "/files/service").getAbsolutePath();
    }

    private String getReceiversPath(String sc_id) {
        return new File(Environment.getExternalStorageDirectory(), ".sketchware/data/" + sc_id + "/files/broadcast").getAbsolutePath();
    }

    private String getGradleValue(String gradleContent, String key) {
        Pattern pattern = Pattern.compile(key + "\\s*=?\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(gradleContent);
        if (matcher.find()) {
            return matcher.group(1);
        }
        pattern = Pattern.compile(key + "\\s*=?\\s*'([^']+)'");
        matcher = pattern.matcher(gradleContent);
        if (matcher.find()) {
            return matcher.group(1);
        }
        pattern = Pattern.compile(key + "\\s*=?\\s*([\\d\\.]+)");
        matcher = pattern.matcher(gradleContent);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String getLocalLibraryPath(String sc_id) {
        return new File(Environment.getExternalStorageDirectory(), ".sketchware/data/" + sc_id + "/local_library").getAbsolutePath();
    }

    private void parseDependencies(File buildGradle, String sc_id) throws Exception {
        String gradleContent = FileUtil.readFile(buildGradle.getAbsolutePath());
        ArrayList<HashMap<String, Object>> libraries = new ArrayList<>();

        Pattern pattern = Pattern.compile("(api|implementation|compileOnly)\\s*['\"](.*?)['\"]");
        Matcher matcher = pattern.matcher(gradleContent);

        while (matcher.find()) {
            String dependency = matcher.group(2);
            HashMap<String, Object> library = new HashMap<>();
            library.put("dependency", dependency);
            libraries.add(library);
        }

        FileUtil.writeFile(getLocalLibraryPath(sc_id), new Gson().toJson(libraries));
    }

    public static void zipFolder(File srcFolder, File destZipFile) throws Exception {
        try (FileOutputStream fileWriter = new FileOutputStream(destZipFile)) {
            try (ZipOutputStream zip = new ZipOutputStream(fileWriter)) {
                addFolderToZip(srcFolder, srcFolder, zip);
                zip.flush();
            }
        }
    }

    private static void addFileToZip(File rootPath, File srcFile, ZipOutputStream zip) throws Exception {

        if (srcFile.isDirectory()) {
            addFolderToZip(rootPath, srcFile, zip);
        } else {
            byte[] buf = new byte[1024];
            int len;
            try (FileInputStream in = new FileInputStream(srcFile)) {
                String name = srcFile.getPath();
                name = name.replace(rootPath.getPath() + "/", "");
                zip.putNextEntry(new ZipEntry(name));
                while ((len = in.read(buf)) > 0) {
                    zip.write(buf, 0, len);
                }
            }
        }
    }

    private static void addFolderToZip(File rootPath, File srcFolder, ZipOutputStream zip) throws Exception {
        File[] srcFolderFiles = srcFolder.listFiles();
        if (srcFolderFiles != null) {
            for (File fileName : srcFolderFiles) {
                addFileToZip(rootPath, fileName, zip);
            }
        }
    }

    //6.3.0 fix1
    public static void createNomediaFileIn(File dir) {
        FileUtil.writeFile(new File(dir, ".nomedia").getAbsolutePath(), "");
    }

    //6.3.0 fix1
    public static void copySafe(File source, File destination) {
        if (!source.exists()) {
            destination.mkdirs();
            createNomediaFileIn(destination);
        } else {
            copy(source, destination);
        }
    }

    public static void copy(File source, File destination) {
        if (source.isDirectory()) {
            if (!destination.exists()) destination.mkdirs();

            String[] files = source.list();
            if (files != null) {

                for (String file : files) {
                    File srcFile = new File(source, file);
                    File destFile = new File(destination, file);

                    copy(srcFile, destFile);
                }
            }
        } else {
            //skip .nomedia files
            if (source.getName().equals(".nomedia")) return;

            try (InputStream in = new FileInputStream(source);
                 OutputStream out = new FileOutputStream(destination)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean zipContainsFile(String zipPath, String fileName) {

        try {
            ZipInputStream zp = new ZipInputStream(new FileInputStream(zipPath));

            ZipEntry en;

            while ((en = zp.getNextEntry()) != null) {
                String name = en.getName();

                if (name.equals(fileName) || name.startsWith(fileName + File.separator)) {
                    zp.close();
                    return true;
                }
            }

            zp.close();

        } catch (Exception ignored) {
        }

        return false;
    }

    /************************ BACKUP ************************/

    public void backup(Context context, String project_name) {
        String customFileName = ConfigActivity.getBackupFileName();

        String versionName = yB.c(lC.b(sc_id), "sc_ver_name");
        String versionCode = yB.c(lC.b(sc_id), "sc_ver_code");
        String pkgName = yB.c(lC.b(sc_id), "my_sc_pkg_name");
        String projectNameOnly = project_name.replace("_d", "").replace(File.separator, "");
        String finalFileName;

        try {
            finalFileName = customFileName
                    .replace("$projectName", projectNameOnly)
                    .replace("$versionCode", versionCode)
                    .replace("$versionName", versionName)
                    .replace("$pkgName", pkgName)
                    .replace("$versionCode", versionCode)
                    .replace("$timeInMs", String.valueOf(Calendar.getInstance(Locale.ENGLISH).getTimeInMillis()));
            Matcher matcher = Pattern.compile("\\$time\\((.*?)\\)").matcher(customFileName);
            while (matcher.find()) {
                finalFileName = finalFileName.replaceFirst(Pattern.quote(Objects.requireNonNull(matcher.group(0))), getFormattedDateFrom(matcher.group(1)));
            }
        } catch (Exception ignored) {
            SketchwareUtil.toastError("Failed To Parse Custom Filename For Backup. Using default");
            // Example name: InternalDemo v1.0 (com.jbk.internal.demo, 1) 2021-12-31T125827
            finalFileName = projectNameOnly + " v" + versionName + " (" + pkgName + ", " + versionCode + ") " + getFormattedDateFrom("yyyy-M-dd'T'HHmmss");
        }
        createBackupsFolder();

        // Init temporary backup folder
        File outFolder = new File(getBackupDir(),
                project_name + "_temp");

        // Init output zip file
        File outZip = new File(getBackupDir() + File.separator + projectNameOnly, finalFileName +
                //Adds all the _d if exists. Otherwise its possible that there'll be an infinite loop
                (project_name.contains("_d") ? project_name.replace(projectNameOnly, "") : "") + "." + EXTENSION);

        // Create a duplicate if already exists (impossible now :3)
        if (outZip.exists()) {
            backup(context, project_name + "_d");
            return;
        }
        //delete temp dir if exist
        if (outFolder.exists()) {
            FileUtil.deleteFile(outFolder.getAbsolutePath());
        }

        // Create necessary folders
        FileUtil.makeDir(outFolder.getAbsolutePath());
        FileUtil.makeDir(new File(getBackupDir() + File.separator + projectNameOnly).getAbsolutePath());

        // Copy data
        File dataF = new File(outFolder, "data");
        FileUtil.makeDir(dataF.getAbsolutePath());
        //6.3.0 fix1
        copySafe(getDataDir(), dataF);

        // Copy res
        File resF = new File(outFolder, "resources");
        FileUtil.makeDir(resF.getAbsolutePath());

        for (String subfolder : resSubfolders) {
            File resSubf = new File(resF, subfolder);
            FileUtil.makeDir(resSubf.getAbsolutePath());

            //6.3.0 fix1
            copySafe(getResDir(subfolder), resSubf);

            // Write an empty file inside each folder (except icons)
            if (!subfolder.equals("icons")) {
                //6.3.0 fix1
                createNomediaFileIn(resSubf);
                //FileUtil.writeFile(new File(resSubf, ".nomedia").getAbsolutePath(), "");
            }
        }

        // Copy project
        File projectF = new File(outFolder, "project");
        copy(getProjectPath(), projectF);

        // Find local libs used and include them in the backup
        if (backupLocalLibs) {
            File localLibs = getLocalLibsPath();

            if (localLibs.exists()) {
                try {
                    JSONArray ja = new JSONArray(FileUtil.readFile(localLibs.getAbsolutePath()));

                    File libsF = new File(outFolder, "local_libs");
                    libsF.mkdirs();

                    for (int i = 0; i < ja.length(); i++) {
                        JSONObject jo = ja.getJSONObject(i);

                        File f = new File(jo.getString("dexPath")).getParentFile();
                        copy(f, new File(libsF, f.getName()));

                    }

                } catch (Exception ignored) {
                }
            }
        }

        // Find custom blocks used and include them in the backup
        if (backupCustomBlocks) {
            CustomBlocksManager cbm = new CustomBlocksManager(context, sc_id);

            Set<ExtraBlockInfo> blocks = new HashSet<>();
            Set<String> block_names = new HashSet<>();
            for (BlockBean bean : cbm.getUsedBlocks()) {
                if (!block_names.contains(bean.opCode)) {
                    block_names.add(bean.opCode);
                    if (cbm.contains(bean.opCode)) {
                        blocks.add(cbm.getExtraBlockInfo(bean.opCode));
                    } else {
                        var block = BlockLoader.getBlockInfo(bean.opCode);
                        blocks.add(block);
                    }
                }
            }

            String json = new Gson().toJson(blocks);

            File customBlocksF = new File(dataF, "custom_blocks");
            FileUtil.writeFile(customBlocksF.getAbsolutePath(), json);
        }

        // Zip final folder
        try {
            zipFolder(outFolder, outZip);
        } catch (Exception e) {
            // An error occurred

//            StringBuilder sb = new StringBuilder();
//            for (StackTraceElement el : e.getStackTrace()) {
//                sb.append(el.toString());
//                sb.append("\n");
//            }

            error = Log.getStackTraceString(e);
            outPath = null;

            return;
        }

        // Delete the temporary folder
        FileUtil.deleteFile(outFolder.getAbsolutePath());

        // Put outZip to global variable
        outPath = outZip;
    }

    private String getFormattedDateFrom(String format) {
        return new SimpleDateFormat(format, Locale.ENGLISH).format(Calendar.getInstance().getTime());
    }

    public File getOutFile() {
        return outPath;
    }

    public void setBackupLocalLibs(boolean b) {
        backupLocalLibs = b;
    }

    public void setBackupCustomBlocks(boolean b) {
        backupCustomBlocks = b;
    }

    /************************ RESTORE ************************/

    public void restore(File swbPath) {
        String name = swbPath.getName();
        if (name.contains(".")) {
            name = name.substring(0, name.lastIndexOf("."));
        }

        restore(swbPath, name);
    }

    public void restoreFromZip(File zipFile) {
        String name = zipFile.getName();
        if (name.contains(".")) {
            name = name.substring(0, name.lastIndexOf("."));
        }

        File outFolder = new File(getBackupDir(), name + "_unzipped");
        if (outFolder.exists()) {
            FileUtil.deleteFile(outFolder.getAbsolutePath());
        }

        if (!unzip(zipFile, outFolder)) {
            error = "couldn't unzip the backup";
            restoreSuccess = false;
            return;
        }

        try {
            File appFolder = new File(outFolder, "app");
            if (!appFolder.exists() || !appFolder.isDirectory()) {
                File[] files = outFolder.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isDirectory()) {
                            if (new File(file, "gradlew").exists()) {
                                appFolder = new File(file, "app");
                                break;
                            }
                        }
                    }
                }
            }

            if (!appFolder.exists() || !appFolder.isDirectory()) {
                throw new Exception("Unable to find 'app' folder in the unzipped project.");
            }

            File buildGradle = new File(appFolder, "build.gradle");
            if (!buildGradle.exists()) {
                 buildGradle = new File(appFolder, "build.gradle.kts");
                 if(!buildGradle.exists()) {
                    throw new Exception("build.gradle or build.gradle.kts not found in app folder");
                 }
            }

            String gradleContent = FileUtil.readFile(buildGradle.getAbsolutePath());
            String packageName = getGradleValue(gradleContent, "applicationId");
            String versionName = getGradleValue(gradleContent, "versionName");
            String versionCode = getGradleValue(gradleContent, "versionCode");

            File manifest = new File(appFolder, "src/main/AndroidManifest.xml");
            if (!manifest.exists()) {
                throw new Exception("AndroidManifest.xml not found");
            }
            String appName = getAppNameFromManifest(manifest);

            HashMap<String, Object> projectMap = new HashMap<>();
            projectMap.put("sc_id", sc_id);
            projectMap.put("my_sc_pkg_name", packageName);
            projectMap.put("sc_ver_name", versionName);
            projectMap.put("sc_ver_code", versionCode);
            projectMap.put("my_ws_name", appName);
            projectMap.put("my_app_name", appName);
            projectMap.put("sketchware_ver", 6);

            File projectFile = new File(getProjectPath().getParentFile(), "project");
            if (!writeEncrypted(projectFile, new Gson().toJson(projectMap))) {
                throw new Exception("couldn't write to the project file");
            }

            copy(new File(appFolder, "src/main/java"), getJavaFilesPath());
            copy(new File(appFolder, "src/main/res"), getResourcesPath());
            copy(new File(appFolder, "src/main/assets"), getAssetsPath());
            copy(new File(appFolder, "src/main/jniLibs"), getNativeLibsPath());

            parseManifest(manifest, sc_id);
            parseDependencies(buildGradle, sc_id);

            restoreSuccess = true;

        } catch (Exception e) {
            error = e.getMessage();
            restoreSuccess = false;
            Log.e("BackupFactory", "Error while restoring from zip", e);
        } finally {
            FileUtil.deleteFile(outFolder.getAbsolutePath());
        }
    }

    private void restore(File swbPath, String name) {

        createBackupsFolder();

        // Init temporary restore folder for unzipping
        File outFolder = new File(getBackupDir(),
                name);

        // Create a duplicate if already exists
        if (outFolder.exists()) {
            restore(swbPath, name + "_d");
            return;
        }

        // Unzip
        if (!unzip(swbPath, outFolder)) {
            error = "couldn't unzip the backup";
            restoreSuccess = false;
            return;
        }

        // Init files
        File project = new File(outFolder, "project");
        File data = new File(outFolder, "data");
        File res = new File(outFolder, "resources");

        HashMap<String, Object> map = getProject(project);

        if (map == null) {
            error = "couldn't read the project file";
            restoreSuccess = false;
            return;
        }

        // Put new sc_id
        map.put("sc_id", sc_id);

        // Write new file
        if (!writeEncrypted(project, new Gson().toJson(map))) {
            error = "couldn't write to the project file";
            restoreSuccess = false;
            return;
        }

        // Copy data
        copy(data, getDataDir());

        // Copy res
        for (String subfolder : resSubfolders) {
            File subf = new File(res, subfolder);

            copySafe(subf, getResDir(subfolder));
        }

        // Create parent folder
        getProjectPath().getParentFile().mkdirs();

        // Copy project
        copy(project, getProjectPath());

        // Copy local libs if they do not exist
        if (backupLocalLibs) {
            File local_libs = new File(outFolder, "local_libs");

            if (local_libs.exists()) {
                File[] local_libs_content = local_libs.listFiles();
                if (local_libs_content != null) {

                    for (File local_lib : local_libs_content) {

                        File local_lib_real_path = new File(getAllLocalLibsDir(), local_lib.getName());

                        if (!local_lib_real_path.exists()) {
                            local_lib_real_path.mkdirs();
                            copy(local_lib, local_lib_real_path);
                        }
                    }
                }
            }
        }

        // Delete temp folder
        FileUtil.deleteFile(outFolder.getAbsolutePath());

        restoreSuccess = true;
    }

    public String getError() {
        return error;
    }

    public boolean isRestoreSuccess() {
        return restoreSuccess;
    }

    /************************ SW METHODS ************************/

    private void createBackupsFolder() {
        // Create the backups folder if it doesn't exist
        String backupsPath = getBackupDir();

        FileUtil.makeDir(backupsPath);
    }

    private File getDataDir() {
        return new File(Environment.getExternalStorageDirectory(),
                ".sketchware/data/" + sc_id);
    }

    private File getResDir(String subfolder) {
        return new File(Environment.getExternalStorageDirectory(),
                ".sketchware/resources/" + subfolder + "/" + sc_id);
    }

    private File getProjectPath() {
        return new File(Environment.getExternalStorageDirectory(),
                ".sketchware/mysc/list/" + sc_id + "/project");
    }

    private File getLocalLibsPath() {
        return new File(Environment.getExternalStorageDirectory(),
                ".sketchware/data/" + sc_id + "/local_library");
    }
}
