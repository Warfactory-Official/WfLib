package com.wf.wflib.pda.client;

import com.wf.wflib.WFLib;
import com.wf.wflib.pda.terminal.TerminalCommandPacket;
import com.wf.wflib.ui.WidgetDef;
import com.wf.wflib.ui.input.TextLine;
import com.wf.wflib.ui.widget.TextField;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.wf.wflib.ui.Ui.at;
import static com.wf.wflib.ui.Ui.stack;

/**
 * Command line ({@code PdaTerminal}): list / rename drones, grid nodes, satellites. Local, never sent to the server:
 * {@code clear}, {@code cmatrix} (Enter quits) and {@code fastfetch} (this machine).
 */
public final class TerminalApp implements PdaApp {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "terminal");
    private static final int LOG_LINES = 200;
    private static final int ALERT = 0xFFD0584A;

    private static final TerminalApp instance = new TerminalApp();
    private static final List<PdaLog.Line> LOG = new ArrayList<>();
    private static final TextLine INPUT = new TextLine(96, TerminalApp::submit);

    private TerminalApp() {
    }

    static void register() {
        log("PDA TERMINAL. TYPE 'help'.", PdaTheme.dim());
        PdaApps.register(instance);
    }

    public static void accept(List<String> lines) {
        for (String line : lines) {
            log(line, line.startsWith("err") ? ALERT : PdaTheme.accent());
        }
    }

    private static void submit(String line) {
        if (MatrixRain.active()) {
            MatrixRain.stop();
            return;
        }
        log("> " + line, PdaTheme.text());
        switch (line.trim().toLowerCase(Locale.ROOT)) {
            case "clear" -> LOG.clear();
            case "cmatrix" -> MatrixRain.start();
            case "fastfetch" -> {
                log("reading this machine...", PdaTheme.dim());
                Fastfetch.run(lines -> lines.forEach(l -> log(l, PdaTheme.text())));
            }
            default -> PacketDistributor.sendToServer(new TerminalCommandPacket(line));
        }
    }

    private static void log(String text, int colour) {
        LOG.add(new PdaLog.Line(text, colour));
        if (LOG.size() > LOG_LINES) {
            LOG.removeFirst();
        }
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable("wflib.pda.app.terminal");
    }

    @Override
    public void closed() {
        MatrixRain.stop();
    }

    @Override
    public boolean visible(LocalPlayer player) {
        return PdaClient.held();
    }

    @Override
    public WidgetDef build() {
        float w = PdaShell.APP_WIDTH - 16;
        float h = PdaShell.APP_HEIGHT - 16;
        return stack(
                at("8", "8", PdaShell.panel(w, h)),
                at("12", "12", new PdaLog(LOG, w - 8, h - 26)),
                at("12", "12", new MatrixRain(w - 8, h - 26)),
                at("12", Float.toString(h - 10), new TextField(INPUT, "> ", w - 8, 13, 0xFF101210, 0xFF787F76,
                        PdaTheme.accent(), PdaTheme.text(), PdaTheme.accent())));
    }
}
