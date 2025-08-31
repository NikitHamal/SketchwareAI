package mod.hey.studios.project.importer;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.utility.FileUtil;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import java.io.FileInputStream;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.google.gson.Gson;
import java.io.RandomAccessFile;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.HashMap;
import mod.hey.studios.util.Helper;


public class AndroidProjectImporter {

    public interface ProgressCallback {
        void onProgress(String message);
    }

    private static final int DEFAULT_BUFFER = 2048;
    private final Context context;
    private final String sc_id;
    private final ProgressCallback progressCallback;
    private File zipFile;
    private File tempDir;

    public AndroidProjectImporter(Context context, String sc_id, ProgressCallback progressCallback) {
        this.context = context;
        this.sc_id = sc_id;
        this.progressCallback = progressCallback;
    }

    public void importProject(File zipFile) {
        this.zipFile = zipFile;
        try {
            unzipProject();
            findAppFolder();
            parseProjectMetadata();
            createSketchwareProject();
            copySourceFiles();
            parseDependencies();
            injectManifestComponents();
            enableLibraries();
        } catch (Exception e) {
            Log.e("AndroidProjectImporter", "Failed to import project", e);
        } finally {
            cleanup();
        }
    }

    private void unzipProject() throws IOException {
        String name = zipFile.getName();
        if (name.contains(".")) {
            name = name.substring(0, name.lastIndexOf("."));
        }
        tempDir = new File(context.getCacheDir(), name + "_unzipped");
        if (tempDir.exists()) {
            FileUtil.deleteFile(tempDir.getAbsolutePath());
        }

        Log.d("AndroidProjectImporter", "Unzipping " + zipFile.getName() + " to " + tempDir.getAbsolutePath());

        try (ZipFile zip = new ZipFile(zipFile)) {
            tempDir.mkdirs();
            Enumeration<? extends ZipEntry> zipFileEntries = zip.entries();
            while (zipFileEntries.hasMoreElements()) {
                ZipEntry entry = zipFileEntries.nextElement();
                String entryName = entry.getName();
                File destFile = new File(tempDir, entryName);
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
        }
    }

    private File appFolder;

    private void findAppFolder() throws Exception {
        appFolder = new File(tempDir, "app");
        if (!appFolder.exists() || !appFolder.isDirectory()) {
            File[] files = tempDir.listFiles();
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
        Log.d("AndroidProjectImporter", "Found app folder at: " + appFolder.getAbsolutePath());
    }

    private String packageName;
    private String appName;
    private String versionName;
    private String versionCode;

    private void parseProjectMetadata() throws Exception {
        File buildGradle = new File(appFolder, "build.gradle");
        if (!buildGradle.exists()) {
             buildGradle = new File(appFolder, "build.gradle.kts");
             if(!buildGradle.exists()) {
                throw new Exception("build.gradle or build.gradle.kts not found in app folder");
             }
        }
        Log.d("AndroidProjectImporter", "Found build.gradle at: " + buildGradle.getAbsolutePath());

        String gradleContent = FileUtil.readFile(buildGradle.getAbsolutePath());
        packageName = getGradleValue(gradleContent, "applicationId");
        versionName = getGradleValue(gradleContent, "versionName");
        versionCode = getGradleValue(gradleContent, "versionCode");
        Log.d("AndroidProjectImporter", "Parsed gradle values: " + packageName + ", " + versionName + ", " + versionCode);

        File manifest = new File(appFolder, "src/main/AndroidManifest.xml");
        if (!manifest.exists()) {
            throw new Exception("AndroidManifest.xml not found");
        }
        appName = getAppNameFromManifest(manifest);
        Log.d("AndroidProjectImporter", "Parsed app name: " + appName);
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

    private void createSketchwareProject() throws Exception {
        String sanitizedAppName = appName.replaceAll("[^a-zA-Z0-9]", "");
        Log.d("AndroidProjectImporter", "Sanitized app name: " + sanitizedAppName);

        HashMap<String, Object> projectMap = new HashMap<>();
        projectMap.put("sc_id", sc_id);
        projectMap.put("my_sc_pkg_name", packageName);
        projectMap.put("sc_ver_name", versionName);
        projectMap.put("sc_ver_code", versionCode);
        projectMap.put("my_ws_name", sanitizedAppName);
        projectMap.put("my_app_name", appName);
        projectMap.put("sketchware_ver", 6);

        File projectFileDir = getProjectPath().getParentFile();
        if (projectFileDir != null && !projectFileDir.exists()) {
            Log.d("AndroidProjectImporter", "Creating project directory: " + projectFileDir.getAbsolutePath());
            projectFileDir.mkdirs();
        }
        File projectFile = new File(projectFileDir, "project");
        Log.d("AndroidProjectImporter", "Writing project file to: " + projectFile.getAbsolutePath());
        if (!writeEncrypted(projectFile, new Gson().toJson(projectMap))) {
            throw new Exception("couldn't write to the project file");
        }
    }

    private File getProjectPath() {
        return new File(FileUtil.getExternalStorageDir(),
                ".sketchware/mysc/list/" + sc_id + "/project");
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

    private void copySourceFiles() {
        Log.d("AndroidProjectImporter", "Copying source files");
        copy(new File(appFolder, "src/main/java"), getJavaFilesPath());
        copy(new File(appFolder, "src/main/res"), getResourcesPath());
        copy(new File(appFolder, "src/main/assets"), getAssetsPath());
        copy(new File(appFolder, "src/main/jniLibs"), getNativeLibsPath());
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

    private File getDataDir() {
        return new File(FileUtil.getExternalStorageDir(),
                ".sketchware/data/" + sc_id);
    }

    private static void copy(File source, File destination) {
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
            if (!source.exists()) return;
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

    private void parseDependencies() throws Exception {
        File buildGradle = new File(appFolder, "build.gradle");
        if (!buildGradle.exists()) {
             buildGradle = new File(appFolder, "build.gradle.kts");
             if(!buildGradle.exists()) {
                // Not a fatal error
                return;
             }
        }
        String gradleContent = FileUtil.readFile(buildGradle.getAbsolutePath());
        FileUtil.writeFile(getLocalLibraryPath(sc_id), "[]"); // Clear the file

        Pattern pattern = Pattern.compile("(api|implementation|compileOnly|testImplementation)\\s*['\"](.*?)['\"]");
        Matcher matcher = pattern.matcher(gradleContent);
        ArrayList<String> dependenciesToResolve = new ArrayList<>();
        while (matcher.find()) {
            dependenciesToResolve.add(matcher.group(2));
        }

        BuildSettings buildSettings = new BuildSettings(sc_id);
        int count = 0;
        ArrayList<String> errors = new ArrayList<>();
        for (String dependency : dependenciesToResolve) {
            count++;
            progressCallback.onProgress("Resolving dependency " + count + "/" + dependenciesToResolve.size() + ": " + dependency);

            String[] parts = dependency.split(":");

            if (parts.length != 3) {
                Log.w("AndroidProjectImporter", "Invalid dependency format: " + dependency);
                continue;
            }

            String groupId = parts[0];
            String artifactId = parts[1];
            String version = parts[2];

            CountDownLatch latch = new CountDownLatch(1);
            final String[] error = {null};

            DependencyResolver resolver = new DependencyResolver(groupId, artifactId, version, false, buildSettings);

            resolver.resolveDependency(new DependencyResolver.DependencyResolverCallback() {
                @Override
                public void onTaskCompleted(@NonNull java.util.List<String> dependencies) {
                    ArrayList<HashMap<String, Object>> enabledLibs = new Gson().fromJson(FileUtil.readFile(getLocalLibraryPath(sc_id)), Helper.TYPE_MAP_LIST);
                    enabledLibs.addAll(dependencies.stream()
                            .map(name -> LocalLibrariesUtil.createLibraryMap(name, dependency))
                            .collect(Collectors.toList()));
                    FileUtil.writeFile(getLocalLibraryPath(sc_id), new Gson().toJson(enabledLibs));
                    latch.countDown();
                }

                @Override
                public void onDownloadError(@NonNull Artifact dep, @NonNull Throwable e) {
                    error[0] = "Failed to download dependency: " + dep;
                    Log.e("AndroidProjectImporter", error[0], e);
                    latch.countDown();
                }

                @Override
                public void onArtifactNotFound(@NonNull Artifact dep) {
                    error[0] = "Artifact not found: " + dep;
                    Log.e("AndroidProjectImporter", error[0]);
                    latch.countDown();
                }
            });

            try {
                latch.await(); // Wait for the download to complete
            } catch (InterruptedException e) {
                error[0] = "Dependency download interrupted";
                Log.e("AndroidProjectImporter", error[0], e);
            }
            if (error[0] != null) {
                errors.add(error[0]);
            }
        }
        if (!errors.isEmpty()) {
            // Don't throw an exception, just log the errors
            Log.e("AndroidProjectImporter", "Failed to resolve some dependencies: " + errors);
        }
    }

    private String getLocalLibraryPath(String sc_id) {
        return new File(FileUtil.getExternalStorageDir(), ".sketchware/data/" + sc_id + "/local_library").getAbsolutePath();
    }

    private void injectManifestComponents() throws Exception {
        File manifestFile = new File(appFolder, "src/main/AndroidManifest.xml");
        if (!manifestFile.exists()) {
            return;
        }

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(manifestFile);
        doc.getDocumentElement().normalize();

        // Get permissions
        ArrayList<String> permissions = new ArrayList<>();
        NodeList permissionNodes = doc.getElementsByTagName("uses-permission");
        for (int i = 0; i < permissionNodes.getLength(); i++) {
            Node node = permissionNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                permissions.add(node.getAttributes().getNamedItem("android:name").getNodeValue());
            }
        }
        FileUtil.writeFile(new File(getPermissionsPath(sc_id)).getAbsolutePath(), new Gson().toJson(permissions));

        // Get application components
        NodeList applicationNodes = doc.getElementsByTagName("application");
        if (applicationNodes.getLength() > 0) {
            Node applicationNode = applicationNodes.item(0);
            NodeList appChildren = applicationNode.getChildNodes();
            StringBuilder appComponents = new StringBuilder();

            for (int i = 0; i < appChildren.getLength(); i++) {
                Node node = appChildren.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    appComponents.append(nodeToString(node));
                }
            }

            File injectionDir = new File(getDataDir(), "Injection/androidmanifest");
            if (!injectionDir.exists()) {
                injectionDir.mkdirs();
            }
            FileUtil.writeFile(new File(injectionDir, "app_components.txt").getAbsolutePath(), appComponents.toString());
        }
    }

    private String nodeToString(Node node) throws Exception {
        StringWriter writer = new StringWriter();
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.transform(new DOMSource(node), new StreamResult(writer));
        return writer.toString();
    }

    private String getPermissionsPath(String sc_id) {
        return new File(FileUtil.getExternalStorageDir(), ".sketchware/data/" + sc_id + "/permission").getAbsolutePath();
    }

    private void enableLibraries() {
        // jC is the project data manager, c(sc_id) gets the library manager (iC)
        iC libraryManager = jC.c(sc_id);
        ProjectLibraryBean appCompat = libraryManager.c();
        appCompat.useYn = "Y";
        if (appCompat.configurations == null) {
            appCompat.configurations = new HashMap<>();
        }
        appCompat.configurations.put("material3", true);
        libraryManager.j(); // j() saves the library settings
        Log.d("AndroidProjectImporter", "Enabled Material 3 library");
    }

    private void cleanup() {
        if (tempDir != null && tempDir.exists()) {
            FileUtil.deleteFile(tempDir.getAbsolutePath());
        }
    }
}
