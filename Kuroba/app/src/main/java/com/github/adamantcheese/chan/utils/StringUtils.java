package com.github.adamantcheese.chan.utils;

import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtils {
    private static final Pattern IMAGE_THUMBNAIL_EXTRACTOR_PATTERN = Pattern.compile("/(\\d{12,32}+)s.(.*)");

    @Nullable
    public static String convertThumbnailUrlToFilenameOnDisk(String url) {
        Matcher matcher = IMAGE_THUMBNAIL_EXTRACTOR_PATTERN.matcher(url);
        if (matcher.find()) {
            String filename = matcher.group(1);
            String extension = matcher.group(2);

            if (filename == null || extension == null) {
                return null;
            }

            if (filename.isEmpty() || extension.isEmpty()) {
                return null;
            }

            return String.format("%s_thumbnail.%s", filename, extension);
        }

        return null;
    }

    @Nullable
    public static String extractFileExtensionFromImageUrl(String url) {
        int index = url.lastIndexOf('.');
        if (index == -1) {
            return null;
        }

        return url.substring(index + 1);
    }

}