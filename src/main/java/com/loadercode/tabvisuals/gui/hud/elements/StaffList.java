/*© 2026 LoxeDLC. All rights reserved.*/
package com.loadercode.tabvisuals.gui.hud.elements;

import com.loadercode.tabvisuals.gui.hud.Hud;
import com.loadercode.tabvisuals.gui.hud.compat.HolyWorldModuleGuard;
import com.loadercode.tabvisuals.gui.hud.draggable.AbstractDraggable;
import com.loadercode.tabvisuals.render.color.ColorAssist;
import com.loadercode.tabvisuals.render.font.FontRenderer;
import com.loadercode.tabvisuals.render.font.Fonts;
import com.loadercode.tabvisuals.render.shape.ShapeProperties;
import com.loadercode.tabvisuals.util.Network;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Staff List — показывает, есть ли на анархии модерация (LoxeDLC port).
 *
 * Список ников зашит прямо в клиент (данные HolyWorld), а «онлайн» определяется
 * по таб-листу текущего сервера: если стафф-игрок есть на HolyWorld — он сразу
 * появляется в HUD.
 *
 * <p>Для игрока в таб-листе читаются <b>префикс</b> и <b>суффикс</b> scoreboard-команды:
 * префикс — это привилегия (например «СОТРУДНИК», «[Админ]»), суффикс — титул
 * (например «с проверкой»). Если команды нет, берётся отображаемое имя игрока.
 * Привилегия показывается плашкой справа, титул — вторым планом перед плашкой;
 * при отсутствии данных показывается «Стафф».
 */
public class StaffList extends AbstractDraggable {
    private static final float PAD = 6f;
    private static final float HEADER_H = 16f;
    private static final float STATUS_H = 16f;
    /** Строка стаффа: от неё центрируются точка, ник, титул и плашка. */
    private static final float ROW_H = 14f;
    private static final float DOT_SIZE = 4f;
    private static final float DOT_GAP = 5f;
    /** Отступ между ником и титулом / между титулом и плашкой. */
    private static final float TITLE_GAP = 6f;
    private static final float PRE_CHIP_GAP = 8f;
    /** Внутренние горизонтальные отступы текста внутри плашки. */
    private static final float CHIP_PAD = 4f;
    private static final float CHIP_H = 12f;

    private static final Pattern FORMATTING = Pattern.compile("§.");
    private static final Pattern BRACKET_TAG = Pattern.compile("\\[([^\\]]{1,24})\\]");
    private static final Pattern SPECIAL_PRIVILEGE = Pattern.compile(
            "админ|admin|владел|owner|разраб|develop|\\bdev\\b|курат|куратор|curator|главн|старш|senior|руковод|founder|создат|гл\\.",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Полный список стаффа HolyWorld (только ники, прочие данные не нужны). */
    private static final Set<String> STAFF_NICKS = new LinkedHashSet<>(List.of(
            "ToplecCHlKA", "BOBURJON", "NeKycok", "CAMAPA", "bodeero", "Asya_Masya", "_wavyy",
            "That0neBear", "Derty_1001", "Heldyy", "Zardem1", "Scary_Pumpkin", "gromaforz",
            "MrPastaKing", "_KArTOfANcHIcK_", "__sny__", "Deri_", "be_pivo_er", "SomeOneDay",
            "LastHitKing", "Fl1ckzzz", "1pirs", "Its_ledsaed", "zerqq", "canute", "W1nturess",
            "IJUSTTILTED", "Microsoft_Client", "Nick122", "Hanrasen", "_KiKLoP_", "LIIAX_u_MAT",
            "fxctar", "Kapibara442", "КОНКУРСЫ", "Mystical_player2", "sweaterholes", "taschenratte",
            "Shotik4562", "Grizzly_", "Recycle_Bin", "turkeyGod", "alewinner", "kisskxd",
            "amogys345", "princeofqueen", "blacstyl", "dimuch75555", "D1HAMIT_B", "Krokodyl_IL1230",
            "DetectedCheats", "artemchyk", "DistaWaka", "WhiteHater", "Pukkopl", "CJ",
            "palefrequency", "DragonDarkxD", "_d3adly_", "Masileka", "MillerXXD", "AutumnGG",
            "HE_D3ADLY", "igarekchotkiy", "Tr1ggered", "stylesreason", "Tvinkfozov", "Berry_lov",
            "LonelyConfident", "Holfys", "Meriwether", "nikolka911", "fedyaka_nyashka",
            "Sunsssseti4", "hfcxjd", "SakuraLegenda", "Malunaa", "danila_112233", "J4sukeHigashikat",
            "bancame", "br1atuxaa", "heccidd", "watveyka", "vasyaW", "52musha", "kusuoinn",
            "iametistebat", "M_A_R_S", "PixenX", "No_Target_Ezzpvp", "dark52521", "CBUHYC",
            "Cvasolir", "trylylegion", "Ya_ni_kto", "LoveWasabika", "mipon123G", "HeaDekBaTuK",
            "APTYP_MUKAELYAN", "Varfolomeev", "Podberesoi", "SuperGrinch1", "vedro_v_potoke",
            "d1nastiya", "neskyW", "nuaraw", "Sicicide", "_proshka2_", "Gawa22335544", "_Skizi0",
            "fullsidee_mode", "ilya_solonr1", "imbruh", "0_am", "Millioner_07", "Fesgri_XD2",
            "KPACOTKA55", "f0reverrr", "the_Revix", "lefort3", "vados48", "Klevicxleb", "WhoIsRem",
            "nothbed", "FlyMchiK", "3anaxMecTN"
    ));

    private FontRenderer titleFont;
    private FontRenderer rowFont;
    private FontRenderer tagFont;
    private FontRenderer statusFont;

    private int onlineCount;

    public StaffList() {
        super("Staff List", 10, 250, 168, 90, true);
    }

    public static StaffList getInstance() {
        return com.loadercode.tabvisuals.gui.hud.Hud.getRepository().get(StaffList.class);
    }

    @Override
    public boolean visible() {
        return HudHelper.isChat(mc.currentScreen) || HolyWorldModuleGuard.isHolyWorldServer();
    }

    /**
     * Вертикальная позиция рисования текста, при которой глифы окажутся
     * по центру прямоугольника высотой {@code rectHeight}: верх глифов = drawY - 3.
     */
    private static float centeredTextY(float rectY, float rectHeight, float fontSize) {
        return rectY + (rectHeight - fontSize) / 2f + 3f;
    }

    /** Текст плашки: сверху «★ » для спецпривилегий. */
    private static String chipText(Row row) {
        return row.special() ? "★ " + row.tag() : row.tag();
    }

    private static float chipWidth(Row row, FontRenderer font) {
        return font.getStringWidth(chipText(row)) + CHIP_PAD * 2f;
    }

    @Override
    public void drawDraggable(DrawContext context) {
        Hud hud = Hud.getInstance();
        if (!HolyWorldModuleGuard.isHolyWorldServer() && !HudHelper.isChat(mc.currentScreen)) return;

        if (titleFont == null) {
            titleFont = Fonts.getSize(13, Fonts.Type.BOLD);
            rowFont = Fonts.getSize(11, Fonts.Type.DEFAULT);
            tagFont = Fonts.getSize(10, Fonts.Type.DEFAULT);
            statusFont = Fonts.getSize(12, Fonts.Type.BOLD);
        }

        List<Row> rows = buildRowsCached(hud);

        int anarchy = Network.getAnarchy();
        String title = "Staff List";
        String anarchyText = anarchy > 0 ? "Анархия-" + anarchy : "";
        boolean moderation = onlineCount > 0;
        String status = moderation ? "Модерация: ЕСТЬ" : "Модерация: НЕТ";

        float titleW = titleFont.getStringWidth(title);
        float anarchyW = anarchyText.isEmpty() ? 0f : rowFont.getStringWidth(anarchyText) + 8f;
        // Бейдж с количеством стаффа онлайн (справа в полосе статуса)
        String count = String.valueOf(onlineCount);
        float badgeW = Math.max(22f, statusFont.getStringWidth(count) + 12f);
        float statusW = statusFont.getStringWidth(status) + 8f + badgeW;
        float rowsW = 0f;
        for (Row row : rows) {
            float w = DOT_SIZE + DOT_GAP + rowFont.getStringWidth(row.nick());
            if (!row.title().isEmpty()) w += TITLE_GAP + tagFont.getStringWidth(row.title());
            if (!row.tag().isEmpty()) w += PRE_CHIP_GAP + chipWidth(row, tagFont);
            rowsW = Math.max(rowsW, w);
        }
        // Пустое состояние: строк нет, рисуется одна подсказка по центру.
        if (rows.isEmpty()) rowsW = tagFont.getStringWidth("Стафф не в сети");

        float contentW = Math.max(titleW + anarchyW, Math.max(statusW, rowsW));
        float width = (float) Math.ceil(PAD + contentW + PAD);
        float rowsBlockH = rows.isEmpty() ? ROW_H : rows.size() * ROW_H;
        float height = (float) Math.ceil(PAD + HEADER_H + STATUS_H + 4f + rowsBlockH + PAD);
        setWidth((int) width);
        setHeight((int) height);
        // the default (10,250) can land below a scaled screen — pull it back
        int sw = mc.getWindow() != null ? mc.getWindow().getScaledWidth() : 0;
        int sh = mc.getWindow() != null ? mc.getWindow().getScaledHeight() : 0;
        if (getX() + width > sw || getY() + height > sh || getX() < 0 || getY() < 0) {
            validPosition();
        }

        float x = getX();
        float y = getY();
        MatrixStack matrix = context.getMatrices();
        int[] gradientColors = ColorAssist.getRectangleGradientColors();

        blur.render(ShapeProperties.create(matrix, x, y, width, height)
                .round(6f)
                .quality(hud.getBlurQuality())
                .color(ColorAssist.getBlurColor())
                .build());
        rectangle.render(ShapeProperties.create(matrix, x, y, width, height)
                .round(6f)
                .thickness(0.1f)
                .outlineColor(ColorAssist.getOutlineColor())
                .color(gradientColors[0], gradientColors[1], gradientColors[2], gradientColors[3])
                .build());

        // Заголовок + номер анархии (оба вертикально по центру шапки)
        titleFont.drawString(matrix, title, x + PAD,
                centeredTextY(y + PAD, HEADER_H, 13f), ColorAssist.getText());
        if (!anarchyText.isEmpty()) {
            rowFont.drawString(matrix, anarchyText, x + width - PAD - rowFont.getStringWidth(anarchyText),
                    centeredTextY(y + PAD, HEADER_H, 11f), ColorAssist.getText(0.6F));
        }

        // Тонкий разделитель под шапкой — отделяет заголовок от полосы статуса
        rectangle.render(ShapeProperties.create(matrix, x + PAD, y + PAD + HEADER_H,
                        width - PAD * 2f, 0.5f)
                .round(0f)
                .color(ColorAssist.getText(0.18F))
                .build());

        // Статус модерации слева + бейдж с количеством стаффа онлайн справа
        int statusColor = moderation
                ? ColorAssist.rgba(255, 84, 84, 255)
                : ColorAssist.rgba(88, 232, 132, 255);
        float statusY = y + PAD + HEADER_H;
        statusFont.drawString(matrix, status, x + PAD,
                centeredTextY(statusY, STATUS_H, 12f), statusColor);

        float badgeH = 13f;
        float badgeX = x + width - PAD - badgeW;
        float badgeY = statusY + (STATUS_H - badgeH) / 2f;
        rectangle.render(ShapeProperties.create(matrix, badgeX, badgeY, badgeW, badgeH)
                .round(4f)
                .thickness(0.5f)
                .outlineColor(moderation
                        ? ColorAssist.rgba(255, 84, 84, 170)
                        : ColorAssist.rgba(150, 150, 150, 110))
                .color(moderation
                        ? ColorAssist.rgba(255, 84, 84, 34)
                        : ColorAssist.rgba(150, 150, 150, 24))
                .build());
        statusFont.drawString(matrix, count, badgeX + (badgeW - statusFont.getStringWidth(count)) / 2f,
                centeredTextY(badgeY, badgeH, 12f), statusColor);

        // Разделитель
        rectangle.render(ShapeProperties.create(matrix, x + PAD, y + PAD + HEADER_H + STATUS_H,
                        width - PAD * 2f, 0.5f)
                .round(0f)
                .color(ColorAssist.getText(0.25F))
                .build());

        // Строки стаффа
        float rowY = y + PAD + HEADER_H + STATUS_H + 4f;
        if (rows.isEmpty()) {
            String hint = "Стафф не в сети";
            tagFont.drawString(matrix, hint, x + (width - tagFont.getStringWidth(hint)) / 2f,
                    centeredTextY(rowY, ROW_H, 10f), ColorAssist.getText(0.45f));
            rowY += ROW_H;
        }
        for (Row row : rows) {
            boolean dimmed = !row.online();
            float textAlpha = dimmed ? 0.4F : 1.0F;

            // Подсветка строки: онлайн-стафф выделен лёгким зелёным фоном
            if (row.online()) {
                rectangle.render(ShapeProperties.create(matrix,
                                x + PAD - 3f, rowY + 1f, width - PAD * 2f + 6f, ROW_H - 2f)
                        .round(3f)
                        .color(ColorAssist.rgba(94, 235, 134, 14))
                        .build());
            }

            // Точка онлайна — ровно по центру строки
            int dotColor = row.online()
                    ? ColorAssist.rgba(94, 235, 134, dimmed ? 110 : 255)
                    : ColorAssist.rgba(150, 150, 150, 110);
            rectangle.render(ShapeProperties.create(matrix,
                            x + PAD, rowY + (ROW_H - DOT_SIZE) / 2f, DOT_SIZE, DOT_SIZE)
                    .round(2f)
                    .color(dotColor)
                    .build());

            float nickX = x + PAD + DOT_SIZE + DOT_GAP;
            rowFont.drawString(matrix, row.nick(), nickX,
                    centeredTextY(rowY, ROW_H, 11f), ColorAssist.getText(textAlpha));

            // Плашка привилегии — прижата вправо, по центру строки
            float chipW = row.tag().isEmpty() ? 0f : chipWidth(row, tagFont);
            float chipX = x + width - PAD - chipW;

            // Титул — вторым цветом перед плашкой (или у правого края, если плашки нет)
            if (!row.title().isEmpty()) {
                float titleTextW = tagFont.getStringWidth(row.title());
                float titleX = row.tag().isEmpty()
                        ? x + width - PAD - titleTextW
                        : chipX - TITLE_GAP - titleTextW;
                tagFont.drawString(matrix, row.title(), titleX,
                        centeredTextY(rowY, ROW_H, 10f),
                        ColorAssist.getText(textAlpha * 0.75f));
            }

            if (row.tag().isEmpty()) {
                rowY += ROW_H;
                continue;
            }

            int chipBg = row.special()
                    ? ColorAssist.rgba(255, 196, 84, dimmed ? 40 : 78)
                    : ColorAssist.rgba(255, 255, 255, dimmed ? 24 : 46);
            int chipOutline = row.special()
                    ? ColorAssist.rgba(255, 196, 84, dimmed ? 70 : 150)
                    : ColorAssist.getOutlineColor();
            float chipY = rowY + (ROW_H - CHIP_H) / 2f;
            rectangle.render(ShapeProperties.create(matrix, chipX, chipY, chipW, CHIP_H)
                    .round(3f)
                    .thickness(0.5f)
                    .outlineColor(chipOutline)
                    .color(chipBg)
                    .build());
            int chipTextColor = row.special()
                    ? ColorAssist.rgba(255, 214, 130, dimmed ? 120 : 255)
                    : ColorAssist.getText(dimmed ? 0.4F : 0.8F);
            tagFont.drawString(matrix, chipText(row), chipX + CHIP_PAD,
                    centeredTextY(chipY, CHIP_H, 10f), chipTextColor);

            rowY += ROW_H;
        }
    }

    // ------------------------------------------------------------------
    // Кэш строк: сборка каждый кадр противоречит тому, от чего она зависит
    // ------------------------------------------------------------------

    /** Состав стаффа и настройки HUD меняются по тику и редким событиям, а не 144 раза в секунду. */
    private static final long ROWS_TTL_MS = 250L;
    private List<Row> cachedRows;
    private long rowsBuiltAt;
    private int cachedRowsKey;

    /**
     * Кэшированная сборка строк. Раньше она шла на каждый кадр: HashMap под весь
     * таб-лист (с {@code toLowerCase} на каждый ник), два ArrayList и {@code new Row}
     * на каждого стаффа — сотни аллокаций в кадр, когда состав меняется
     * единожды, когда кто-то зашёл или вышел. Ключ покрывает настройки и размер
     * таб-листа, TTL добивает те случаи, где состав изменился без смены числа
     * (сменился титул/префикс) — максимум четверть секунды отставания.
     */
    private List<Row> buildRowsCached(Hud hud) {
        int players = mc.getNetworkHandler() != null ? mc.getNetworkHandler().getPlayerList().size() : -1;
        boolean showOffline = hud.staffShowOffline;
        boolean showDetails = hud.staffShowPrivileges;
        int maxRows = Math.max(1, hud.staffMaxRows);

        int key = (showOffline ? 1 << 30 : 0)
                | (showDetails ? 1 << 29 : 0)
                | ((maxRows & 0xFFFF) << 13)
                | (players & 0x1FFF);

        long now = System.currentTimeMillis();
        if (cachedRows != null && key == cachedRowsKey && now - rowsBuiltAt < ROWS_TTL_MS) {
            return cachedRows;
        }
        cachedRows = buildRows(showOffline, showDetails, maxRows);
        cachedRowsKey = key;
        rowsBuiltAt = now;
        return cachedRows;
    }

    private List<Row> buildRows(boolean showOffline, boolean showDetails, int maxRows) {
        Map<String, PlayerListEntry> online = new HashMap<>();
        if (mc.getNetworkHandler() != null) {
            for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                if (entry == null || entry.getProfile() == null) continue;
                online.put(entry.getProfile().getName().toLowerCase(Locale.ROOT), entry);
            }
        }

        List<Row> onlineRows = new ArrayList<>();
        List<String> offlineNicks = new ArrayList<>();
        for (String nick : STAFF_NICKS) {
            PlayerListEntry entry = online.get(nick.toLowerCase(Locale.ROOT));
            if (entry != null) {
                String tag = showDetails ? privilegeOf(entry) : "";
                String title = showDetails ? titleOf(entry) : "";
                onlineRows.add(new Row(nick, true, title, tag,
                        showDetails && SPECIAL_PRIVILEGE.matcher(tag).find()));
            } else if (showOffline) {
                offlineNicks.add(nick);
            }
        }
        onlineCount = onlineRows.size();

        // Лимит строк распространяется и на онлайн — иначе HUD вылезает за экран.
        List<Row> rows;
        if (onlineRows.size() >= maxRows) {
            rows = new ArrayList<>(onlineRows.subList(0, maxRows));
        } else {
            rows = new ArrayList<>(onlineRows);
            int budget = maxRows - rows.size();
            int added = 0;
            for (String nick : offlineNicks) {
                if (added >= budget) break;
                rows.add(new Row(nick, false, "", "", false));
                added++;
            }
        }

        if (rows.isEmpty()) {
            onlineCount = 0;
        }
        return rows;
    }

    // ------------------------------------------------------------------
    // Данные таб-листа: привилегия (префикс) и титул (суффикс)
    // ------------------------------------------------------------------

    /** Суффикс/префикс без кодов форматирования и лишних пробелов. */
    private static String clean(String value) {
        if (value == null || value.isEmpty()) return "";
        return FORMATTING.matcher(value).replaceAll("").replaceAll("\\s+", " ").trim();
    }

    private static String clip(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }

    /**
     * Привилегия игрока. Приоритет: префикс scoreboard-команды («СОТРУДНИК»),
     * затем скобочный тег в отображаемом имени («[Админ] Nick»), затем часть
     * имени до ника. Если данных нет — «Стафф».
     */
    private static String privilegeOf(PlayerListEntry entry) {
        String raw = "";
        Team team = entry.getScoreboardTeam();
        if (team != null) raw = clean(team.getPrefix().getString());

        if (raw.isEmpty()) {
            Text display = entry.getDisplayName();
            if (display != null) raw = clean(display.getString());
        }
        if (raw.isEmpty()) return "Стафф";

        Matcher bracket = BRACKET_TAG.matcher(raw);
        if (bracket.find()) {
            String tag = bracket.group(1).trim();
            if (!tag.isEmpty()) return clip(tag, 18);
        }

        String head = headBeforeNick(entry, raw);
        return head.isEmpty() ? "Стафф" : clip(head, 18);
    }

    /**
     * Титул игрока: суффикс scoreboard-команды («с проверкой»), иначе —
     * хвост отображаемого имени после ника. Пустая строка, если титула нет.
     */
    private static String titleOf(PlayerListEntry entry) {
        Team team = entry.getScoreboardTeam();
        if (team != null) {
            String suffix = clean(team.getSuffix().getString());
            if (!suffix.isEmpty()) return clip(suffix, 24);
        }

        Text display = entry.getDisplayName();
        if (display == null) return "";
        String full = clean(display.getString());
        String nick = entry.getProfile().getName();
        int at = full.toLowerCase(Locale.ROOT).indexOf(nick.toLowerCase(Locale.ROOT));
        if (at < 0 || at + nick.length() >= full.length()) return "";
        String tail = full.substring(at + nick.length()).trim();
        return tail.isEmpty() ? "" : clip(tail, 24);
    }

    /** Часть строки до ника игрока: «СОТРУДНИК LonelyConfident» → «СОТРУДНИК». */
    private static String headBeforeNick(PlayerListEntry entry, String raw) {
        String nick = entry.getProfile().getName();
        int at = raw.toLowerCase(Locale.ROOT).indexOf(nick.toLowerCase(Locale.ROOT));
        if (at < 0) return raw.trim();
        if (at == 0) return "";
        return raw.substring(0, at).trim();
    }

    private record Row(String nick, boolean online, String title, String tag, boolean special) {
    }
}
