package fr.harvesterhud;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Affiche l'etat de la houe "Harvester" (utilisation, XP, niveau, recolte).
 * Les donnees sont lues dans le texte (lore) de l'item.
 * Touche H : ecran de placement (glisser = deplacer, molette = taille).
 */
public class HarvesterHudClient implements ClientModInitializer {
    static final Pattern USE = Pattern.compile("Utilisation\\s*:\\s*([\\d\\s.,]+)/([\\d\\s.,]+)");
    static final Pattern XP  = Pattern.compile("XP\\s*:\\s*([\\d\\s.,]+)/([\\d\\s.,]+)");
    static final Pattern LVL = Pattern.compile("niv\\.\\s*(\\d+)/(\\d+)");
    static final Pattern HAR = Pattern.compile("R[ée]colte\\s*:\\s*(x\\d+)");

    static int x = 6, y = 6;
    static float scale = 1.0f;
    static final Path CFG = Paths.get("config", "harvesterhud.txt");

    static KeyMapping openEditor;

    static String name = "", use = "", xp = "", lvl = "", rec = "";
    static double usePct = 0, xpPct = 0;
    static boolean found = false;

    @Override
    public void onInitializeClient() {
        load();
        openEditor = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.harvesterhud.edit", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H,
                KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openEditor.consumeClick()) mc.setScreen(new EditScreen());
            scan(mc);
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("harvesterhud", "hud"),
                (g, dt) -> { if (found) draw(g, Minecraft.getInstance()); });
    }

    static void scan(Minecraft mc) {
        found = false;
        if (mc.player == null) return;
        ItemStack s = mc.player.getMainHandItem();
        if (!isHarvester(s)) {
            s = ItemStack.EMPTY;
            for (int i = 0; i < 9; i++) {
                ItemStack c = mc.player.getInventory().getItem(i);
                if (isHarvester(c)) { s = c; break; }
            }
        }
        if (s.isEmpty()) return;
        name = s.getHoverName().getString();
        ItemLore lore = s.get(DataComponents.LORE);
        if (lore == null) return;
        for (Component line : lore.lines()) {
            String t = line.getString();
            Matcher m;
            if ((m = USE.matcher(t)).find()) { use = clean(m.group(1)) + "/" + clean(m.group(2)); usePct = pct(m); }
            else if ((m = XP.matcher(t)).find()) { xp = clean(m.group(1)) + "/" + clean(m.group(2)); xpPct = pct(m); }
            else if ((m = LVL.matcher(t)).find()) lvl = m.group(1) + "/" + m.group(2);
            else if ((m = HAR.matcher(t)).find()) rec = m.group(1);
        }
        found = true;
    }

    static boolean isHarvester(ItemStack s) {
        return !s.isEmpty() && s.getHoverName().getString().toLowerCase().contains("harvester");
    }
    static String clean(String v) { return v.replaceAll("\\s", ""); }
    static double pct(Matcher m) {
        try {
            double a = Double.parseDouble(clean(m.group(1)).replace(',', '.'));
            double b = Double.parseDouble(clean(m.group(2)).replace(',', '.'));
            return b <= 0 ? 0 : Math.min(1, a / b);
        } catch (Exception e) { return 0; }
    }

    static final int W = 140, H = 58;

    static void draw(GuiGraphicsExtractor g, Minecraft mc) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.fill(0, 0, W, H, 0x90000000);
        g.text(mc.font, name, 4, 4, 0xFFFF5555, true);
        g.text(mc.font, "Niv. " + lvl + "  Récolte " + rec, 4, 15, 0xFFFFFFFF, true);
        g.text(mc.font, "Util. " + use, 4, 26, 0xFFFFFFFF, true);
        bar(g, 4, 36, W - 8, usePct, 0xFF55FF55);
        g.text(mc.font, "XP " + xp, 4, 42, 0xFFFFFFFF, true);
        bar(g, 4, 52, W - 8, xpPct, 0xFF55FFFF);
        g.pose().popMatrix();
    }
    static void bar(GuiGraphicsExtractor g, int bx, int by, int w, double p, int col) {
        g.fill(bx, by, bx + w, by + 3, 0xFF333333);
        g.fill(bx, by, bx + (int) (w * p), by + 3, col);
    }

    static void save() {
        try {
            Files.createDirectories(CFG.getParent());
            Files.writeString(CFG, x + "," + y + "," + scale);
        } catch (Exception ignored) {}
    }
    static void load() {
        try {
            String[] p = Files.readString(CFG).trim().split(",");
            x = Integer.parseInt(p[0]); y = Integer.parseInt(p[1]); scale = Float.parseFloat(p[2]);
        } catch (Exception ignored) {}
    }

    /** Ecran de placement : glisser-deposer + molette pour la taille. */
    static class EditScreen extends Screen {
        boolean drag; int ox, oy;
        EditScreen() { super(Component.literal("Harvester HUD")); }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
            super.extractRenderState(g, mx, my, dt);
            if (!found) { name = "Harvester (exemple)"; use = "17500/17500"; xp = "0/48"; lvl = "1/100"; rec = "x2"; usePct = 1; }
            draw(g, minecraft);
            String msg = "Glisse pour déplacer - molette = taille - Échap = valider";
            g.text(font, msg, (width - font.width(msg)) / 2, height - 20, 0xFFFFFFFF, true);
        }
        boolean inside(double mx, double my) {
            return mx >= x && mx <= x + W * scale && my >= y && my <= y + H * scale;
        }
        @Override
        public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
            if (inside(e.x(), e.y())) { drag = true; ox = (int) (e.x() - x); oy = (int) (e.y() - y); return true; }
            return super.mouseClicked(e, dbl);
        }
        @Override
        public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
            if (drag) { x = (int) e.x() - ox; y = (int) e.y() - oy; return true; }
            return super.mouseDragged(e, dx, dy);
        }
        @Override
        public boolean mouseReleased(MouseButtonEvent e) {
            drag = false; save(); return super.mouseReleased(e);
        }
        @Override
        public boolean mouseScrolled(double mx, double my, double sx, double sy) {
            scale = Math.max(0.5f, Math.min(3f, scale + (float) sy * 0.1f)); save(); return true;
        }
        @Override
        public void onClose() { save(); super.onClose(); }
    }
}
