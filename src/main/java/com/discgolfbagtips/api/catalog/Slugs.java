package com.discgolfbagtips.api.catalog;

import java.util.Locale;
import java.util.regex.Pattern;

public final class Slugs {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

    private Slugs() {
    }

    public static String of(String value) {
        if (value == null) {
            return "";
        }
        String slug = NON_ALPHANUMERIC.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("-");
        return slug.replaceAll("^-+", "").replaceAll("-+$", "");
    }
}
