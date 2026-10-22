/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.content.pm;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.LruCache;

/**
 * Builds a TV banner for an app that does not ship one.
 *
 * <p>Android TV shows an app in the apps row through its banner. An app that has none is drawn
 * with a generic icon instead, which in a 16:9 card looks out of place next to the apps that do
 * have a banner. The banner has to live in the app itself though, so the fallback is drawn here
 * instead, from the icon that is already there: the icon is reduced to its most colourful tone
 * and used as the banner's background, with the icon itself and the app's name over it.
 *
 * <p>The shape follows the TV app icon guidelines, which ask for an opaque 16:9 banner that
 * fills the card, carries the app's name so the app can be identified, and is supplied at
 * xxxhdpi.
 *
 * <p>This is only reached through {@link ApplicationInfo#loadBanner}, so apps that ship a
 * banner are never affected.
 */
final class GeneratedTvBanner {

    /** The size Android TV asks a banner to be, at xxxhdpi. */
    private static final int WIDTH = 640;
    private static final int HEIGHT = 360;

    /** The density those pixels are at, so the system scales the banner like any other asset. */
    private static final int BANNER_DENSITY = DisplayMetrics.DENSITY_XHIGH;

    /** How much of the banner height the icon takes. */
    private static final int ICON_PERCENT = 53;

    /** A place the name may be broken, and which takes up no room when it is not taken. */
    private static final char BREAK_HINT = '\u200B';

    /** How far the icon sits from the left edge, as a fraction of the banner width. */
    private static final float ICON_INSET = 0.09f;

    /** The gap between the icon and the name, as a fraction of the banner width. */
    private static final float LABEL_GAP = 0.05f;

    /** How far the name is kept from the right edge, as a fraction of the banner width. */
    private static final float LABEL_INSET = 0.09f;

    /** The size the name starts at, as a fraction of the banner height. */
    private static final float LABEL_MAX = 0.20f;

    /**
     * The size the name is not shrunk below, as a fraction of the banner height.
     *
     * <p>Past this a name is cut short instead: text small enough to miss across a room is not
     * better than no name at all.
     */
    private static final float LABEL_MIN = 0.12f;

    /**
     * The tallest the name may grow, as a fraction of the banner height.
     *
     * <p>A name that is grown to fill the width stops here if nothing else stops it before,
     * so a very short one does not climb out of the band every other name is sized within.
     * The ordinary fit can never reach this: {@link #LABEL_MAX} is far below it.
     */
    private static final float LABEL_HEIGHT_MAX = 0.6f;

    /**
     * How long a name has to be, before it is allowed to run over two lines.
     *
     * <p>A name this length or shorter stays on one line, which keeps it as large as it will
     * go. Past that it may break across two lines, because a name squeezed onto one line is
     * either too small to read or gets cut off.
     *
     * <p>The length is measured in width units rather than characters, so that a name in a
     * script whose glyphs are drawn twice as wide is not let past on character count alone.
     * See {@link #widthUnits}.
     */
    private static final int SINGLE_LINE_MAX_LENGTH = 12;

    /** Neutral colours for an icon that has no colour worth using. */
    private static final float FALLBACK_HUE = 0.6f;

    /** How much of the app's colour survives into the background. */
    private static final float MAX_SATURATION = 0.26f;

    /** The band the background lives in: dark, as Android TV asks for. */
    private static final float LIGHTNESS = 0.20f;

    /** Icon is sampled at this size; the banner itself is drawn at banner resolution. */
    private static final int SAMPLE_SIZE = 64;

    /** Enough for a couple of pages of a TV launcher. */
    private static final int CACHE_SIZE = 32;

    private static final LruCache<String, Drawable> sCache = new LruCache<>(CACHE_SIZE);

    private GeneratedTvBanner() {}

    /**
     * Returns a banner drawn from the app icon, or {@code null} when no icon can be loaded.
     */
    static Drawable get(ApplicationInfo info, PackageManager pm) {
        if (info == null || pm == null) {
            return null;
        }
        final String key = info.packageName;
        final Drawable cached = sCache.get(key);
        if (cached != null) {
            return cached;
        }
        final Drawable banner = create(info, pm);
        if (banner != null) {
            sCache.put(key, banner);
        }
        return banner;
    }

    private static Drawable create(ApplicationInfo info, PackageManager pm) {
        final Drawable icon = info.loadIcon(pm);
        if (icon == null) {
            return null;
        }

        final Bitmap sample = render(icon, SAMPLE_SIZE);
        if (sample == null) {
            return null;
        }

        final int background = extractColor(sample);
        sample.recycle();

        final Bitmap banner = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(banner);
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        canvas.drawColor(background);

        // The icon sits on the left and the name runs beside it, as the TV banners that ship
        // with apps do. The icon is what the app is recognised by, so it keeps its size; the
        // name is fitted to the room beside it and grown to fill that room where it would
        // otherwise stop short.
        final int size = HEIGHT * ICON_PERCENT / 100;
        final int iconLeft = (int) (WIDTH * ICON_INSET);
        final int iconTop = (HEIGHT - size) / 2;
        final Rect dst = new Rect(iconLeft, iconTop, iconLeft + size, iconTop + size);
        final Bitmap scaled = render(icon, size);
        if (scaled != null) {
            canvas.drawBitmap(scaled, null, dst, paint);
            scaled.recycle();
        }

        final int labelLeft = iconLeft + size + (int) (WIDTH * LABEL_GAP);
        final StaticLayout label = fitLabel(info.loadLabel(pm), labelLeft, background);
        if (label != null) {
            canvas.save();
            canvas.translate(labelLeft, (HEIGHT - label.getHeight()) / 2f);
            label.draw(canvas);
            canvas.restore();
        }

        banner.setDensity(BANNER_DENSITY);
        return new BitmapDrawable(Resources.getSystem(), banner);
    }

    /**
     * Lays the name out beside the icon, at the largest size that still reads.
     *
     * <p>The name is never allowed off the edge of the banner. It starts at {@link #LABEL_MAX}
     * and shrinks only as far as {@link #LABEL_MIN}, and only if it has to.
     *
     * <p>A name longer than {@link #SINGLE_LINE_MAX_LENGTH} may be spread over two lines: a name
     * too small to read on one line is harder to make out than the same name over two, which is
     * how the banners that ship with apps present a long name. Where it breaks is up to the
     * text layout: at a space, at a change of case inside a word, or at a hyphenation point when
     * the name is a single long word with nowhere else to break.
     *
     * <p>A name short enough to stop before the edge of its row is then grown until it fills
     * it, so the room the name was given is never left half empty. {@link #LABEL_HEIGHT_MAX}
     * keeps a very short name from growing taller than a banner is meant to hold.
     */
    private static StaticLayout fitLabel(CharSequence name, int left, int background) {
        if (name == null || name.length() == 0) {
            return null;
        }

        final CharSequence text = withBreaks(name);
        final int width = Math.max(1, WIDTH - left - (int) (WIDTH * LABEL_INSET));

        // A name over the length may be spread over two lines, which reads better than the
        // smaller name the same room would allow on one.
        final int lines = widthUnits(name) > SINGLE_LINE_MAX_LENGTH ? 2 : 1;

        final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(textColor(background));
        paint.setTypeface(Typeface.DEFAULT);

        // Take the largest size the name still fits at. Coming down in fixed steps overshoots:
        // a name a hair too wide at the top size would drop a whole step and leave the rest of
        // the row empty, so the size is looked for instead of stepped to.
        int low = (int) (HEIGHT * LABEL_MIN);
        int high = (int) (HEIGHT * LABEL_MAX);
        int size = low;
        while (low <= high) {
            final int mid = (low + high) / 2;
            paint.setTextSize(mid);
            if (fits(buildLines(text, paint, width, lines, name), text.length())) {
                size = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        // A name that does not fit whole even at the smallest size is cut short, which is
        // better than leaving the row empty.
        paint.setTextSize(size);
        final StaticLayout layout = buildLines(text, paint, width, lines, name);
        if (!fits(layout, text.length())) {
            return layout;
        }
        return filled(layout, text, paint, width, lines, name);
    }

    /**
 * How wide the name is, in letters of the Latin alphabet.
 *
 * <p>Counting characters alone would let a name through on a length the room cannot hold: the
 * glyphs of Chinese, Japanese and Korean are drawn about twice as wide as a Latin letter, so a
 * short name in one of them runs as long as a much longer Latin one. Each of those counts twice
 * here, and everything else once.
 */
private static int widthUnits(CharSequence name) {
        int units = 0;
        for (int i = 0; i < name.length(); i++) {
            units += isWide(name.charAt(i)) ? 2 : 1;
        }
        return units;
    }

    /**
     * Whether the character is one of those drawn about twice as wide as a Latin letter.
     *
     * <p>The CJK ranges and the wide forms of the other East Asian scripts, along with the
     * pictographs, which are square like the characters they sit beside.
     */
    private static boolean isWide(char c) {
        return c >= 0x1100 && (c <= 0x115f                    // Hangul Jamo
                || (c >= 0x2e80 && c <= 0x303e)              // CJK radicals, Kangxi
                || (c >= 0x3041 && c <= 0x33ff)              // kana, CJK compatibility
                || (c >= 0x3400 && c <= 0x4dbf)              // CJK extension A
                || (c >= 0x4e00 && c <= 0x9fff)              // CJK unified
                || (c >= 0xa000 && c <= 0xa4cf)              // Yi
                || (c >= 0xa960 && c <= 0xa97c)              // Hangul Jamo extended
                || (c >= 0xac00 && c <= 0xd7a3)              // Hangul syllables
                || (c >= 0xf900 && c <= 0xfaff)              // CJK compatibility
                || (c >= 0xfe10 && c <= 0xfe19)              // vertical forms
                || (c >= 0xfe30 && c <= 0xfe6f)              // CJK compatibility forms
                || (c >= 0xff01 && c <= 0xff60)              // full-width forms
                || (c >= 0xffe0 && c <= 0xffe6)              // full-width signs
                || (c >= 0x1b000 && c <= 0x1b16f)             // kana supplement, extended
                || (c >= 0x1f300 && c <= 0x1f64f)             // pictographs, emoticons
                || (c >= 0x1f900 && c <= 0x1f9ff)             // supplemental pictographs
                || (c >= 0x20000 && c <= 0x3fffd));          // CJK extensions B and later
    }

    /**
     * Marks the points inside the name where a line may be broken.
     *
     * <p>An app whose name runs its words together, as "AutoFramerateBooster" does, has no
     * space for the layout to break at, so a long one would have to be cut off or shrunk out
     * of legibility. The changes of case stand for the word boundaries the name leaves out, so
     * those are marked instead.
     */
    private static CharSequence withBreaks(CharSequence name) {
        final int length = name.length();
        final StringBuilder text = new StringBuilder(length + 8);
        int copied = 0;
        for (int i = 1; i < length; i++) {
            if (isCaseBoundary(name, i)) {
                text.append(name, copied, i).append(BREAK_HINT);
                copied = i;
            }
        }
        if (copied == 0) {
            return name;
        }
        return text.append(name, copied, length);
    }

    /**
     * Whether the name may be broken just before the character at {@code i}.
     *
     * <p>Two shapes count: a lower case letter followed by an upper case one, as in
     * "AutoFramerate", and the upper case letter that begins a capitalised word after a run of
     * capitals, as in "AVPlayer".
     */
    private static boolean isCaseBoundary(CharSequence name, int i) {
        final char before = name.charAt(i - 1);
        final char at = name.charAt(i);
        if (Character.isLowerCase(before) && Character.isUpperCase(at)) {
            return true;
        }
        return Character.isUpperCase(before) && Character.isUpperCase(at)
                && i + 1 < name.length() && Character.isLowerCase(name.charAt(i + 1));
    }

    /**
     * Lays the name out over at most {@code maxLines} lines, cut short if it still runs over.
     *
     * <p>A name that is one long word has no space to break at, so the hyphenation patterns
     * decide where it breaks rather than it being cut at whatever point the width runs out.
     */
    private static StaticLayout buildLines(CharSequence text, TextPaint paint, int width,
            int maxLines, CharSequence name) {
        final StaticLayout.Builder builder = StaticLayout.Builder.obtain(
                text, 0, text.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setMaxLines(maxLines)
                .setIncludePad(false);
        if (hasNoBreakOfItsOwn(text, name)) {
            builder.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL);
        }
        return builder.build();
    }

    /**
     * Grows a laid-out name until it fills the width of its row.
     *
     * <p>A short name laid out by {@link #fitLabel} stops short of the edge and leaves the rest
     * of the row empty, which is smaller than the same room could hold. The name is grown by
     * its widest line until that line reaches the edge, and by no further than it can rise
     * before losing {@link #LABEL_HEIGHT_MAX}.
     *
     * <p>Every size the layout is asked for draws the name the same way but scaled, so the
     * places it can break at do not move when the name is grown: the layout keeps exactly the
     * shape it was given, only larger. Nothing else can go wrong with it either, but for the
     * rounding of the last line, which can leave the grown name a hair too wide to be laid out
     * whole; if that happens the size is eased back until it fits again. The name is never
     * shrunk: fitting is {@link #fitLabel}'s business, this only ever adds size.
     */
    private static StaticLayout filled(StaticLayout layout, CharSequence text, TextPaint paint,
            int width, int maxLines, CharSequence name) {
        final int widest = widestLine(layout);
        if (widest <= 0) {
            return layout;
        }
        final float scale = Math.min(
                (float) width / widest,
                (float) (HEIGHT * LABEL_HEIGHT_MAX) / layout.getHeight());
        if (scale <= 1f) {
            return layout;
        }
        final float baseSize = paint.getTextSize();
        for (float grown = scale; grown > 1f; grown *= 0.98f) {
            paint.setTextSize(baseSize * grown);
            final StaticLayout candidate = buildLines(text, paint, width, maxLines, name);
            if (fits(candidate, text.length())) {
                return candidate;
            }
        }
        paint.setTextSize(baseSize);
        return layout;
    }

    /** The width of the widest line the layout holds. */
    private static int widestLine(StaticLayout layout) {
        int widest = 0;
        for (int line = 0; line < layout.getLineCount(); line++) {
            widest = Math.max(widest, (int) layout.getLineWidth(line));
        }
        return widest;
    }

    /**
     * Whether the name is a single long word that was given no place to break.
     *
     * <p>Where the name has break hints or spaces of its own there is somewhere for the layout
     * to break, so the hyphenation patterns are not consulted.
     */
    private static boolean hasNoBreakOfItsOwn(CharSequence text, CharSequence name) {
        if (text.length() != name.length()) {
            return false;
        }
        if (widthUnits(name) <= SINGLE_LINE_MAX_LENGTH) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            if (Character.isWhitespace(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the whole name made it into the layout.
     *
     * <p>Two things can hide a cut-short name. The layout stores no end-of-last-line of its own:
     * the end of line {@code i} is written where the start of line {@code i + 1} would be, so
     * the count itself is the slot to ask. And whatever the count of lines does not hold is
     * squeezed into the last visible one and marked with an ellipsis, which does not move the
     * line's end: the name can stop where it began. So the ellipsis of the last line is asked
     * for as well, and only a layout that placed the name whole, without either, answers yes.
     */
    private static boolean fits(StaticLayout layout, int length) {
        return layout.getEllipsisCount(layout.getLineCount() - 1) == 0
                && layout.getLineStart(layout.getLineCount()) == length;
    }

    /**
     * Picks a text colour that reads against the background.
     *
     * <p>The background is always dark by construction, so white is the usual answer, but the
     * band it lives in is wide enough that a pale icon can still lighten it.
     */
    private static int textColor(int background) {
        final double luminance = (0.2126 * Color.red(background)
                + 0.7152 * Color.green(background)
                + 0.0722 * Color.blue(background)) / 255.0;
        return luminance > 0.45 ? 0xde000000 : Color.WHITE;
    }

    /** Draws a drawable into a square bitmap, or returns {@code null} if it cannot be drawn. */
    private static Bitmap render(Drawable drawable, int size) {
        if (drawable == null || size <= 0) {
            return null;
        }
        try {
            final Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            final Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, size, size);
            drawable.draw(canvas);
            return bitmap;
        } catch (RuntimeException e) {
            // A drawable from an app must never take the caller down with it.
            return null;
        }
    }

    /**
     * Picks the banner's background colour from the icon.
     *
     * <p>Colours are grouped into 4-4-4 buckets and scored by how often they occur, weighted by
     * how saturated they are, with a penalty for the near-white and near-black that flat
     * monochrome icons tend to produce.
     */
    private static int extractColor(Bitmap bitmap) {
        final int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0,
                bitmap.getWidth(), bitmap.getHeight());

        final int[] counts = new int[4096];
        for (int pixel : pixels) {
            if (Color.alpha(pixel) < 0x40) {
                continue;
            }
            final int r = Color.red(pixel);
            final int g = Color.green(pixel);
            final int b = Color.blue(pixel);
            counts[((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4)]++;
        }

        int bestKey = -1;
        long bestScore = 0;
        for (int key = 0; key < counts.length; key++) {
            final int count = counts[key];
            if (count == 0) {
                continue;
            }
            final int color = unquantize(key);
            final int max = Math.max(Color.red(color), Math.max(Color.green(color),
                    Color.blue(color)));
            final int min = Math.min(Color.red(color), Math.min(Color.green(color),
                    Color.blue(color)));
            // Prefer a colourful, mid-tone colour that actually covers pixels.
            long score = (long) count * (max - min);
            if (max > 0xf0 && min > 0xd0) {
                score /= 4; // washed out
            } else if (max < 0x20 && min < 0x20) {
                score /= 4; // almost black
            }
            if (score > bestScore) {
                bestScore = score;
                bestKey = key;
            }
        }

        if (bestKey < 0) {
            return hslToColor(FALLBACK_HUE, MAX_SATURATION, LIGHTNESS);
        }

        final float[] hsl = toHsl(unquantize(bestKey));
        return hslToColor(hsl[0], MAX_SATURATION, LIGHTNESS);
    }

    /**
     * Builds the background colour from the hue of the app, kept deliberately quiet.
     *
     * <p>Android TV asks for a dark, neutral surface behind the logo and lets the logo carry the
     * emphasis, and dark tones are also the cheap ones on a TV panel. So the app's hue is kept
     * for recognition while the saturation and lightness are pinned into a narrow, muted band.
     */
    private static int hslToColor(float hue, float saturation, float lightness) {
        final float c = (1f - Math.abs(2f * lightness - 1f)) * saturation;
        final float h = hue * 6f;
        final float x = c * (1f - Math.abs(h % 2f - 1f));
        final float m = lightness - c / 2f;
        final float r;
        final float g;
        final float b;
        if (h < 1f) {
            r = c;
            g = x;
            b = 0f;
        } else if (h < 2f) {
            r = x;
            g = c;
            b = 0f;
        } else if (h < 3f) {
            r = 0f;
            g = c;
            b = x;
        } else if (h < 4f) {
            r = 0f;
            g = x;
            b = c;
        } else if (h < 5f) {
            r = x;
            g = 0f;
            b = c;
        } else {
            r = c;
            g = 0f;
            b = x;
        }
        return Color.rgb(Math.round((r + m) * 255), Math.round((g + m) * 255),
                Math.round((b + m) * 255));
    }

    /** Converts a colour to hue, saturation and lightness, all in the range 0..1. */
    private static float[] toHsl(int color) {
        final float r = Color.red(color) / 255f;
        final float g = Color.green(color) / 255f;
        final float b = Color.blue(color) / 255f;
        final float max = Math.max(r, Math.max(g, b));
        final float min = Math.min(r, Math.min(g, b));
        final float l = (max + min) / 2f;
        if (max == min) {
            return new float[] {0f, 0f, l};
        }
        final float d = max - min;
        final float s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
        float h;
        if (max == r) {
            h = (g - b) / d + (g < b ? 6f : 0f);
        } else if (max == g) {
            h = (b - r) / d + 2f;
        } else {
            h = (r - g) / d + 4f;
        }
        return new float[] {h / 6f, s, l};
    }

    /** Rebuilds a representative colour from its 4-4-4 bucket. */
    private static int unquantize(int key) {
        final int r = ((key >> 8) & 0xf) * 0x11;
        final int g = ((key >> 4) & 0xf) * 0x11;
        final int b = (key & 0xf) * 0x11;
        return Color.rgb(r, g, b);
    }
}