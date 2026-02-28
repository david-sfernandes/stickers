package com.example.samplestickerapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class UserStickerPackStore {
    private static final String ROOT_FOLDER = "user_sticker_packs";
    private static final String PACKS_FOLDER = "packs";
    private static final String METADATA_FILE = "contents.json";
    private static final String DEFAULT_TRAY_FILE = "tray.png";
    private static final String PREFS_NAME = "sticker_pack_prefs";
    private static final String PREF_HIDDEN_PACKS = "hidden_packs";
    private static final int TRAY_SIZE_PX = 96;
    private static final String DEFAULT_STICKER_EMOJI = "\uD83D\uDE42";

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

        final String identifier = generateIdentifier(name, packsArray);
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

    static synchronized StickerPack addStickerToPack(@NonNull Context context, @NonNull String identifier, @NonNull Uri imageUri) throws IOException {
        final JSONObject root = readOrCreateMetadata(context);
        final JSONObject packObject = findPackObject(root, identifier);
        if (packObject == null) {
            throw new IOException("pack not found");
        }

        final byte[] stickerWebp = StickerImageProcessor.createStickerWebp(context, imageUri);
        final String fileName = String.format(Locale.US, "sticker_%d.webp", System.currentTimeMillis());
        final File packDir = getPackDir(context, identifier);
        if (!packDir.exists() && !packDir.mkdirs()) {
            throw new IOException("failed to create pack folder");
        }
        writeBytes(new File(packDir, fileName), stickerWebp);

        try {
            final JSONArray stickers = packObject.getJSONArray("stickers");
            final JSONObject sticker = new JSONObject();
            sticker.put("image_file", fileName);
            final JSONArray emojis = new JSONArray();
            emojis.put(DEFAULT_STICKER_EMOJI);
            sticker.put("emojis", emojis);
            stickers.put(sticker);
            packObject.put("image_data_version", String.valueOf(System.currentTimeMillis()));

            if (stickers.length() == 1) {
                createTrayFromSticker(packDir, stickerWebp);
            }
        } catch (JSONException e) {
            throw new IOException("failed to update sticker metadata", e);
        }

        writeMetadata(context, root);
        final List<StickerPack> packs = loadStickerPacks(context);
        final StickerPack updatedPack = findPackByIdentifier(packs, identifier);
        if (updatedPack == null) {
            throw new IOException("failed to reload pack");
        }
        return updatedPack;
    }

    static synchronized StickerPack removeStickerFromPack(@NonNull Context context, @NonNull String identifier, @NonNull String stickerFileName) throws IOException {
        final JSONObject root = readOrCreateMetadata(context);
        final JSONObject packObject = findPackObject(root, identifier);
        if (packObject == null) {
            throw new IOException("pack not found");
        }
        final JSONArray stickers = packObject.optJSONArray("stickers");
        if (stickers == null) {
            throw new IOException("stickers not found");
        }

        boolean removed = false;
        for (int i = 0; i < stickers.length(); i++) {
            final JSONObject sticker = stickers.optJSONObject(i);
            if (sticker != null && stickerFileName.equals(sticker.optString("image_file"))) {
                stickers.remove(i);
                removed = true;
                break;
            }
        }
        if (!removed) {
            throw new IOException("sticker not found");
        }

        try {
            packObject.put("image_data_version", String.valueOf(System.currentTimeMillis()));
        } catch (JSONException e) {
            throw new IOException("failed to update sticker metadata", e);
        }
        writeMetadata(context, root);
        final File stickerFile = getStickerFile(context, identifier, stickerFileName);
        if (stickerFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            stickerFile.delete();
        }

        final List<StickerPack> packs = loadStickerPacks(context);
        final StickerPack updatedPack = findPackByIdentifier(packs, identifier);
        if (updatedPack == null) {
            throw new IOException("failed to reload pack");
        }
        return updatedPack;
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

    static synchronized void hidePack(@NonNull Context context, @NonNull String identifier) {
        final Set<String> hidden = getHiddenPacks(context);
        hidden.add(identifier);
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putStringSet(PREF_HIDDEN_PACKS, hidden).apply();
    }

    static synchronized boolean isPackHidden(@NonNull Context context, @NonNull String identifier) {
        return getHiddenPacks(context).contains(identifier);
    }

    static File getStickerFile(@NonNull Context context, @NonNull String identifier, @NonNull String fileName) {
        return new File(getPackDir(context, identifier), fileName);
    }

    static boolean isCustomPack(@NonNull Context context, @NonNull String identifier) {
        for (StickerPack pack : loadStickerPacks(context)) {
            if (identifier.equals(pack.identifier)) {
                return true;
            }
        }
        return false;
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
        final File metadataFile = getMetadataFile(context);
        if (!metadataFile.exists()) {
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

        try (FileInputStream inputStream = new FileInputStream(metadataFile)) {
            final byte[] content = readAllBytes(inputStream);
            return new JSONObject(new String(content, StandardCharsets.UTF_8));
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
            pack.setCustomPack(true);
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
        final File metadataFile = getMetadataFile(context);
        try (FileOutputStream outputStream = new FileOutputStream(metadataFile, false)) {
            outputStream.write(root.toString().getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        }
    }

    private static void createDefaultTrayIcon(@NonNull File packDir) throws IOException {
        final Bitmap trayBitmap = Bitmap.createBitmap(TRAY_SIZE_PX, TRAY_SIZE_PX, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(trayBitmap);
        canvas.drawColor(Color.TRANSPARENT);
        writeBitmapAsPng(trayBitmap, new File(packDir, DEFAULT_TRAY_FILE));
    }

    private static void createTrayFromSticker(@NonNull File packDir, @NonNull byte[] stickerWebp) throws IOException {
        final Bitmap sticker = StickerImageProcessor.decodeBitmap(new ByteArrayInputStream(stickerWebp));
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

    private static byte[] readAllBytes(@NonNull FileInputStream inputStream) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            final byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    @NonNull
    private static String generateIdentifier(@NonNull String name, @NonNull JSONArray existingPacks) {
        String base = name.toLowerCase(Locale.US).replaceAll("[^a-z0-9._\\- ]", "").trim().replace(" ", "_");
        if (TextUtils.isEmpty(base)) {
            base = "pack";
        }
        if (base.length() > 100) {
            base = base.substring(0, 100);
        }
        String candidate = base;
        int suffix = 1;
        while (containsIdentifier(existingPacks, candidate)) {
            candidate = base + "_" + suffix;
            suffix++;
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

    @NonNull
    private static Set<String> getHiddenPacks(@NonNull Context context) {
        final Set<String> stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getStringSet(PREF_HIDDEN_PACKS, new HashSet<>());
        return stored == null ? new HashSet<>() : new HashSet<>(stored);
    }
}
