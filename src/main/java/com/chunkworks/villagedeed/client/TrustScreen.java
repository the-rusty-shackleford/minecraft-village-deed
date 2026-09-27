/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed.client;

import com.chunkworks.villagedeed.TrustList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/** The trust screen (D-0004): every player this world has seen, the viewer's own roster ticked,
 * online players first and in green; a click on a name trusts or untrusts that player in all the
 * viewer's villages. Drawn like Warehouse Manager's trust panel, so the two read as one family.
 * It paints the listing the server sends and forwards clicks; the server decides everything. */
public final class TrustScreen extends Screen {
    private static final int WIDTH = 176, ROW = 12, PAD = 6, INNER = WIDTH - 2 * PAD, MAX_HEIGHT = 222;
    private static final int WHITE = 0xFFFFFF, GREY = 0xA0A0A0, ONLINE = 0x55FF55, OFFLINE = 0xD0D0D0;
    private TrustList.Listing listing;
    private int scroll, left, top, panelHeight;

    public TrustScreen(TrustList.Listing listing) {
        super(Component.translatable("screen.villagedeed.trust.title"));
        this.listing = listing;
    }
    /** effects: shows the listing in the open trust screen, or opens one when the server asked; a
     * refresh that arrives with the screen closed is dropped. */
    public static void accept(TrustList.Listing listing) {
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof TrustScreen screen) { screen.listing = listing; screen.clampScroll(); }
        else if (listing.open()) mc.setScreen(new TrustScreen(listing));
    }
    /** effects: the listing on show. */
    public TrustList.Listing listing() { return listing; }
    /** effects: the panel's left edge on screen. */
    public int panelLeft() { return left; }
    /** effects: the top of the row with that index, scrolled. */
    public int rowTop(int index) { return top + header() + (index - scroll) * ROW; }

    @Override protected void init() {
        panelHeight = Math.min(MAX_HEIGHT, height - 20);
        left = (width - WIDTH) / 2;
        top = (height - panelHeight) / 2;
        clampScroll();
    }
    @Override public boolean isPauseScreen() { return false; }
    private Component hint() {
        return switch (listing.villages()) {
            case 0 -> Component.translatable("screen.villagedeed.trust.hint.none");
            case 1 -> Component.translatable("screen.villagedeed.trust.hint.one");
            default -> Component.translatable("screen.villagedeed.trust.hint.many", listing.villages());
        };
    }
    private int header() { return PAD + 11 + 9 * font.split(hint(), INNER).size() + 4; }
    private int rowsShown() { return Math.max(0, (panelHeight - header() - PAD) / ROW); }
    private void clampScroll() { scroll = Mth.clamp(scroll, 0, Math.max(0, listing.rows().size() - rowsShown())); }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x0 = left, x1 = left + WIDTH, y0 = top, y1 = top + panelHeight;
        g.fill(x0, y0, x1, y1, 0xFF3F3F3F);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xF0181818);
        // The count, since a ticked name can sit below the fold of a long list.
        long ticked = listing.rows().stream().filter(r -> r.trusted()).count();
        g.drawString(font, font.plainSubstrByWidth(Component.translatable("screen.villagedeed.trust.title.count", ticked).getString(), INNER), x0 + PAD, y0 + PAD, WHITE, false);
        int y = y0 + PAD + 11;
        for (var line : font.split(hint(), INNER)) { g.drawString(font, line, x0 + PAD, y, GREY, false); y += 9; }
        var rows = listing.rows();
        if (rows.isEmpty()) {
            y = top + header();
            for (var line : font.split(Component.translatable("screen.villagedeed.trust.empty"), INNER)) { g.drawString(font, line, x0 + PAD, y, GREY, false); y += 9; }
            return;
        }
        int hovered = rowAt(mouseX, mouseY), shown = rowsShown();
        g.enableScissor(x0 + 1, top + header(), x1 - 1, y1 - PAD);
        for (int i = scroll; i < rows.size() && i < scroll + shown; i++) {
            var r = rows.get(i);
            int rowTop = rowTop(i);
            if (i == hovered) g.fill(x0 + 1, rowTop, x1 - 1, rowTop + ROW, 0x40FFFFFF);
            // The box and the name are drawn apart so every name starts at one column: "x" is
            // wider than a space in the game's font.
            int colour = r.online() ? ONLINE : OFFLINE, nameX = x0 + PAD + font.width("[x] ");
            g.drawString(font, r.trusted() ? "[x]" : "[ ]", x0 + PAD, rowTop + 2, colour, false);
            g.drawString(font, font.plainSubstrByWidth(r.name(), x1 - 4 - nameX), nameX, rowTop + 2, colour, false);
        }
        g.disableScissor();
        if (rows.size() > shown && shown > 0) {
            // A thin thumb on the right edge says there is more to scroll to, and how much.
            int track = shown * ROW, thumb = Math.max(8, track * shown / rows.size());
            int at = top + header() + (track - thumb) * scroll / (rows.size() - shown);
            g.fill(x1 - 4, at, x1 - 2, at + thumb, 0xFF8B8B8B);
        }
    }
    /** effects: the index of the row under the point, or -1. */
    private int rowAt(double mouseX, double mouseY) {
        int rowsTop = top + header();
        if (mouseX < left || mouseX >= left + WIDTH || mouseY < rowsTop || mouseY >= top + panelHeight - PAD) return -1;
        int i = scroll + (int) ((mouseY - rowsTop) / ROW);
        return i < listing.rows().size() && i < scroll + rowsShown() ? i : -1;
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int i = rowAt(mouseX, mouseY);
        if (i < 0 || button != 0) return super.mouseClicked(mouseX, mouseY, button);
        var r = listing.rows().get(i);
        PacketDistributor.sendToServer(new TrustList.Toggle(r.id(), !r.trusted()));
        return true;
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= left && mouseX < left + WIDTH && mouseY >= top && mouseY < top + panelHeight) {
            scroll -= (int) Math.signum(scrollY);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    /** effects: the inventory key closes the screen as it closes a chest; Escape as ever. */
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
