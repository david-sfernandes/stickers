package com.example.samplestickerapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.AtomicFile;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class UserStickerPackStore {
    private static final String ROOT_FOLDER = "user_sticker_packs";
    private static final String PACKS_FOLDER = "packs";
    private static final String METADATA_FILE = "contents.json";
    private static final String DEFAULT_TRAY_FILE = "tray.png";
    private static final String TRAY_SOURCE_FIELD = "tray_source";
    private static final int TRAY_SIZE_PX = 96;
    static final int MAX_STICKERS_PER_PACK = 30;
    static final int MIN_STICKERS_PER_PACK = 3;

    private UserStickerPackStore() {
    }

    static synchronized List<StickerPack> loadStickerPacks(@NonNull Context context) {
        try {
            return parsePacksFromMetadata(readOrCreateMetadata(context));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    static synchronized StickerPack createPack(@NonNull Context context, @NonNull String name, @NonNull String publisher) throws IOException {
        final JSONObject root = readOrCreateMetadata(context);
        final JSONArray packsArray = root.optJSONArray("sticker_packs");
        if (packsArray == null) {
            throw new IOException("invalid metadata");
        }

        final String identifier = generateIdentifier(packsArray);
        final JSONObject newPack = new JSONObject();
        try {
            newPack.put("identifier", identifier);
            newPack.put("name", name);
            newPack.put("publisher", publisher);
            newPack.put("tray_image_file", DEFAULT_TRAY_FILE);
            newPack.put("image_data_version", String.valueOf(System.currentTimeMillis()));
            newPack.put("animated_sticker_pack", false);
            newPack.put("stickers", new JSONArray());
            packsArray.put(newPack);
        } catch (JSONException e) {
            throw new IOException("failed to build pack metadata", e);
        }

        final File packDir = getPackDir(context, identifier);
        if (!packDir.exists() && !packDir.mkdirs()) {
            throw new IOException("failed to create pack folder");
        }
        createDefaultTrayIcon(packDir);
        writeMetadata(context, root);

        final List<StickerPack> packs = loadStickerPacks(context);
        final StickerPack createdPack = findPackByIdentifier(packs, identifier);
        if (createdPack == null) {
            throw new IOException("failed to create pack");
        }
        return createdPack;
    }

    /**
     * @param replacedFileName the sticker to be substituted, keeping its position. Null to add a new one.
     */
    static synchronized StickerPack addStickerToPack(@NonNull Context context, @NonNull String identifier, @NonNull byte[] stickerWebp, @NonNull List<String> emojis, boolean animated, @Nullable String replacedFileName) throws IOException {
        final JSONObject root = readOrCreateMetadata(context);
        final JSONObject packObject = requirePackObject(root, identifier);
        final File packDir = getPackDir(context, identifier);
        if (!packDir.exists() && !packDir.mkdirs()) {
            throw new IOException("failed to create pack folder");
        }
        final String fileName = String.format(Locale.US, "sticker_%d.webp", System.currentTimeMillis());

        try {
            final JSONArray stickers = packObject.getJSONArray("stickers");
            final int replacedIndex = replacedFileName == null ? -1 : findStickerIndex(stickers, replacedFileName);
            if (replacedFileName != null && replacedIndex < 0) {
                throw new StickerException(R.string.error_sticker_not_found);
            }
            if (replacedFileName == null && stickers.length() >= MAX_STICKERS_PER_PACK) {
                throw new StickerException(R.string.error_pack_full);
            }
            final int otherStickers = stickers.length() - (replacedIndex >= 0 ? 1 : 0);
            if (otherStickers > 0 && packObject.optBoolean("animated_sticker_pack", false) != animated) {
                throw new StickerException(R.string.error_pack_type_mismatch);
            }
            writeBytes(new File(packDir, fileName), stickerWebp);

            final JSONObject sticker = new JSONObject();
            sticker.put("image_file", fileName);
            sticker.put("emojis", new JSONArray(emojis));
            if (replacedIndex >= 0) {
                stickers.put(replacedIndex, sticker);
            } else {
                stickers.put(sticker);
            }
            packObject.put("animated_sticker_pack", animated);
            if (replacedFileName != null && replacedFileName.equals(packObject.optString(TRAY_SOURCE_FIELD))) {
                packObject.put(TRAY_SOURCE_FIELD, fileName);
            }
            packObject.put("image_data_version", String.valueOf(System.currentTimeMillis()));
            updateTray(packDir, packObject);
        } catch (JSONException e) {
            throw new IOException("failed to update sticker metadata", e);
        }

        writeMetadata(context, root);
        if (replacedFileName != null) {
            deleteFile(new File(packDir, replacedFileName));
        }
        return reloadPack(context, identifier);
    }

    static synchronized StickerPack removeStickerFromPack(@NonNull Context context, @NonNull String identifier, @NonNull String stickerFileName) throws IOException {
        final JSONObject root = readOrCreateMetadata(context);
        final JSONObject packObject = requirePackObject(root, identifier);
        final JSONArray stickers = packObject.optJSONArray("stickers");
        final int index = stickers == null ? -1 : findStickerIndex(stickers, stickerFileName);
        if (index < 0) {
            throw new StickerException(R.string.error_sticker_not_found);
        }
        stickers.remove(index);

        try {
            if (stickerFileName.equals(packObject.optString(TRAY_SOURCE_FIELD))) {
                packObject.remove(TRAY_SOURCE_FIELD);
            }
            if (stickers.length() == 0) {
                // An empty pack can receive any type of sticker again.
                packObject.put("animated_sticker_pack", false);
            }
            packObject.put("image_data_version", String.valueOf(System.currentTimeMillis()));
        } catch (JSONException e) {
            throw new IOException("failed to update sticker metadata", e);
        }
        updateTray(getPackDir(context, identifier), packObject);
        writeMetadata(context, root);
        deleteFile(getStickerFile(context, identifier, stickerFileName));
        return reloadPack(context, identifier);
    }

    static synchronized StickerPack updatePackDetails(@NonNull Context context, @NonNull String identifier, @NonNull String name, @NonNull String publisher) throws IOException {
        final JSONObject root = readOrCreateMetadata(context);
        final JSONObject packObject = requirePackObject(root, identifier);
        try {
            packObject.put("name", name);
            packObject.put("publisher", publisher);
        } catch (JSONException e) {
            throw new IOException("failed to update pack metadata", e);
        }
        writeMetadata(context, root);
        return reloadPack(context, identifier);
    }

    static synchronized StickerPack setTraySticker(@NonNull Context context, @NonNull String identifier, @NonNull String stickerFileName) throws IOException {
        final JSONObject root = readOrCreateMetadata(context);
        final JSONObject packObject = requirePackObject(root, identifier);
        try {
            packObject.put(TRAY_SOURCE_FIELD, stickerFileName);
            packObject.put("image_data_version", String.valueOf(System.currentTimeMillis()));
        } catch (JSONException e) {
            throw new IOException("failed to update pack metadata", e);
        }
        updateTray(getPackDir(context, identifier), packObject);
        writeMetadata(context, root);
        return reloadPack(context, identifier);
    }

    static synchronized boolean deletePack(@NonNull Context context, @NonNull String identifier) throws IOException {
        final JSONObject root = readOrCreateMetadata(context);
        final JSONArray packs = root.optJSONArray("sticker_packs");
        if (packs == null) {
            return false;
        }

        boolean removed = false;
        for (int i = 0; i < packs.length(); i++) {
            final JSONObject pack = packs.optJSONObject(i);
            if (pack != null && identifier.equals(pack.optString("identifier"))) {
                packs.remove(i);
                removed = true;
                break;
            }
        }

        if (!removed) {
            return false;
        }

        writeMetadata(context, root);
        deleteRecursively(getPackDir(context, identifier));
        return true;
    }

    static File getStickerFile(@NonNull Context context, @NonNull String identifier, @NonNull String fileName) {
        return new File(getPackDir(context, identifier), fileName);
    }

    @NonNull
    private static StickerPack reloadPack(@NonNull Context context, @NonNull String identifier) throws IOException {
        final StickerPack pack = findPackByIdentifier(loadStickerPacks(context), identifier);
        if (pack == null) {
            throw new IOException("failed to reload pack");
        }
        return pack;
    }

    @NonNull
    private static JSONObject requirePackObject(@NonNull JSONObject root, @NonNull String identifier) throws StickerException {
        final JSONObject pack = findPackObject(root, identifier);
        if (pack == null) {
            throw new StickerException(R.string.pack_not_found_error);
        }
        return pack;
    }

    private static int findStickerIndex(@NonNull JSONArray stickers, @NonNull String fileName) {
        for (int i = 0; i < stickers.length(); i++) {
            final JSONObject sticker = stickers.optJSONObject(i);
            if (sticker != null && fileName.equals(sticker.optString("image_file"))) {
                return i;
            }
        }
        return -1;
    }

    @Nullable
    private static StickerPack findPackByIdentifier(@NonNull List<StickerPack> packs, @NonNull String identifier) {
        for (StickerPack pack : packs) {
            if (identifier.equals(pack.identifier)) {
                return pack;
            }
        }
        return null;
    }

    @Nullable
    private static JSONObject findPackObject(@NonNull JSONObject root, @NonNull String identifier) {
        final JSONArray packs = root.optJSONArray("sticker_packs");
        if (packs == null) {
            return null;
        }
        for (int i = 0; i < packs.length(); i++) {
            final JSONObject pack = packs.optJSONObject(i);
            if (pack != null && identifier.equals(pack.optString("identifier"))) {
                return pack;
            }
        }
        return null;
    }

    @NonNull
    private static JSONObject readOrCreateMetadata(@NonNull Context context) throws IOException {
        final AtomicFile metadataFile = new AtomicFile(getMetadataFile(context));
        final boolean interruptedWrite = new File(metadataFile.getBaseFile().getPath() + ".bak").exists();
        if (!metadataFile.getBaseFile().exists() && !interruptedWrite) {
            final JSONObject root = new JSONObject();
            try {
                root.put("android_play_store_link", "");
                root.put("ios_app_store_link", "");
                root.put("sticker_packs", new JSONArray());
            } catch (JSONException e) {
                throw new IOException("failed to initialize metadata", e);
            }
            writeMetadata(context, root);
            return root;
        }

        try {
            return new JSONObject(new String(metadataFile.readFully(), StandardCharsets.UTF_8));
        } catch (JSONException e) {
            throw new IOException("invalid metadata json", e);
        }
    }

    @NonNull
    private static List<StickerPack> parsePacksFromMetadata(@NonNull JSONObject root) {
        final List<StickerPack> packs = new ArrayList<>();
        final String androidPlayStoreLink = root.optString("android_play_store_link");
        final String iosAppStoreLink = root.optString("ios_app_store_link");
        final JSONArray packsArray = root.optJSONArray("sticker_packs");
        if (packsArray == null) {
            return packs;
        }
        for (int i = 0; i < packsArray.length(); i++) {
            final JSONObject packJson = packsArray.optJSONObject(i);
            if (packJson == null) {
                continue;
            }
            final String identifier = packJson.optString("identifier");
            final String name = packJson.optString("name");
            final String publisher = packJson.optString("publisher");
            final String trayImageFile = packJson.optString("tray_image_file", DEFAULT_TRAY_FILE);
            final String imageDataVersion = packJson.optString("image_data_version", String.valueOf(System.currentTimeMillis()));
            final boolean avoidCache = packJson.optBoolean("avoid_cache", false);
            final boolean animated = packJson.optBoolean("animated_sticker_pack", false);
            if (TextUtils.isEmpty(identifier) || TextUtils.isEmpty(name) || TextUtils.isEmpty(publisher)) {
                continue;
            }
            final StickerPack pack = new StickerPack(
                    identifier,
                    name,
                    publisher,
                    trayImageFile,
                    packJson.optString("publisher_email"),
                    packJson.optString("publisher_website"),
                    packJson.optString("privacy_policy_website"),
                    packJson.optString("license_agreement_website"),
                    imageDataVersion,
                    avoidCache,
                    animated
            );
            pack.setAndroidPlayStoreLink(androidPlayStoreLink);
            pack.setIosAppStoreLink(iosAppStoreLink);
            pack.setStickers(parseStickers(packJson.optJSONArray("stickers")));
            packs.add(pack);
        }
        return packs;
    }

    @NonNull
    private static List<Sticker> parseStickers(@Nullable JSONArray stickersArray) {
        final List<Sticker> stickers = new ArrayList<>();
        if (stickersArray == null) {
            return stickers;
        }
        for (int i = 0; i < stickersArray.length(); i++) {
            final JSONObject stickerJson = stickersArray.optJSONObject(i);
            if (stickerJson == null) {
                continue;
            }
            final String imageFile = stickerJson.optString("image_file");
            if (TextUtils.isEmpty(imageFile)) {
                continue;
            }
            final List<String> emojis = new ArrayList<>();
            final JSONArray emojisArray = stickerJson.optJSONArray("emojis");
            if (emojisArray != null) {
                for (int j = 0; j < emojisArray.length(); j++) {
                    final String emoji = emojisArray.optString(j);
                    if (!TextUtils.isEmpty(emoji)) {
                        emojis.add(emoji);
                    }
                }
            }
            final Sticker sticker = new Sticker(imageFile, emojis, stickerJson.optString("accessibility_text", null));
            final long size = stickerJson.optLong("size", 0);
            if (size > 0) {
                sticker.setSize(size);
            }
            stickers.add(sticker);
        }
        return stickers;
    }

    private static void writeMetadata(@NonNull Context context, @NonNull JSONObject root) throws IOException {
        final File rootDir = getRootDir(context);
        if (!rootDir.exists() && !rootDir.mkdirs()) {
            throw new IOException("failed to create root folder");
        }
        final AtomicFile metadataFile = new AtomicFile(getMetadataFile(context));
        FileOutputStream outputStream = null;
        try {
            outputStream = metadataFile.startWrite();
            outputStream.write(root.toString().getBytes(StandardCharsets.UTF_8));
            metadataFile.finishWrite(outputStream);
        } catch (IOException e) {
            if (outputStream != null) {
                metadataFile.failWrite(outputStream);
            }
            throw e;
        }
    }

    private static void createDefaultTrayIcon(@NonNull File packDir) throws IOException {
        final Bitmap trayBitmap = Bitmap.createBitmap(TRAY_SIZE_PX, TRAY_SIZE_PX, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(trayBitmap);
        canvas.drawColor(Color.TRANSPARENT);
        writeBitmapAsPng(trayBitmap, new File(packDir, DEFAULT_TRAY_FILE));
    }

    /**
     * The tray shows the sticker the user picked or else the first one of the pack.
     */
    private static void updateTray(@NonNull File packDir, @NonNull JSONObject packObject) throws IOException {
        final JSONArray stickers = packObject.optJSONArray("stickers");
        if (stickers == null || stickers.length() == 0) {
            createDefaultTrayIcon(packDir);
            return;
        }
        String source = packObject.optString(TRAY_SOURCE_FIELD);
        if (TextUtils.isEmpty(source) || findStickerIndex(stickers, source) < 0) {
            source = stickers.optJSONObject(0).optString("image_file");
        }
        final Bitmap sticker = StickerImageProcessor.decodeFirstFrame(readBytes(new File(packDir, source)));
        if (sticker == null) {
            return;
        }
        final Bitmap tray = Bitmap.createScaledBitmap(sticker, TRAY_SIZE_PX, TRAY_SIZE_PX, true);
        writeBitmapAsPng(tray, new File(packDir, DEFAULT_TRAY_FILE));
    }

    private static void writeBitmapAsPng(@NonNull Bitmap bitmap, @NonNull File file) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(file, false)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                throw new IOException("failed to write tray image");
            }
            outputStream.flush();
        }
    }

    private static void writeBytes(@NonNull File file, @NonNull byte[] bytes) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(file, false)) {
            outputStream.write(bytes);
            outputStream.flush();
        }
    }

    @NonNull
    private static byte[] readBytes(@NonNull File file) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(file);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            final byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    /**
     * The name of the pack is kept out of the identifier, which is also the name of its folder.
     */
    @NonNull
    private static String generateIdentifier(@NonNull JSONArray existingPacks) {
        long suffix = System.currentTimeMillis();
        String candidate = "pack_" + suffix;
        while (containsIdentifier(existingPacks, candidate)) {
            suffix++;
            candidate = "pack_" + suffix;
        }
        return candidate;
    }

    private static boolean containsIdentifier(@NonNull JSONArray packs, @NonNull String identifier) {
        for (int i = 0; i < packs.length(); i++) {
            final JSONObject pack = packs.optJSONObject(i);
            if (pack != null && identifier.equals(pack.optString("identifier"))) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static File getRootDir(@NonNull Context context) {
        return new File(context.getFilesDir(), ROOT_FOLDER);
    }

    @NonNull
    private static File getMetadataFile(@NonNull Context context) {
        return new File(getRootDir(context), METADATA_FILE);
    }

    @NonNull
    private static File getPackDir(@NonNull Context context, @NonNull String identifier) {
        return new File(new File(getRootDir(context), PACKS_FOLDER), identifier);
    }

    private static void deleteRecursively(@NonNull File file) {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            final File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static void deleteFile(@NonNull File file) {
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }
}
