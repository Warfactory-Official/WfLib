package com.wf.wflib.pda.client;

import com.wf.wflib.ui.api.Signals;
import com.wf.wflib.ui.api.Subject;
import com.wf.wflib.ui.api.WidgetDef;
import com.wf.wflib.ui.api.input.TextBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;
import com.wf.wflib.pda.api.client.PdaApp;
import com.wf.wflib.pda.api.client.PdaApps;
import com.wf.wflib.pda.api.client.PdaShell;
import com.wf.wflib.core.api.WFLib;

import static com.wf.wflib.ui.api.Ui.at;
import static com.wf.wflib.ui.api.Ui.stack;
import static com.wf.wflib.ui.api.Ui.text;

/**
 * Dev-only demo of {@link PdaTextBox}: a count (1..100), a price (2 decimals, 0..999.99) and a name. Only registered outside
 * production with {@code -Dwflib.pda.fieldsDemo=true}, so the PDA test harness can type into real fields.
 */
public final class PdaFieldsDemo implements PdaApp {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "fields_demo");
    private static final PdaFieldsDemo INSTANCE = new PdaFieldsDemo();
    private static String submitted = "NOTHING SUBMITTED";
    private static final TextBox COUNT = TextBox.integer(5, 1, 100).onSubmit(s -> submitted = "COUNT " + s);
    private static final TextBox PRICE = TextBox.decimal(8, 2, 0, 999.99).onSubmit(s -> submitted = "PRICE " + s);
    private static final TextBox NAME = TextBox.text(24).onSubmit(s -> submitted = "NAME " + s);

    private PdaFieldsDemo() {
    }

    public static void register() {
        if (FMLEnvironment.production || !Boolean.getBoolean("wflib.pda.fieldsDemo")) {
            return;
        }
        Subject<PdaFieldsDemo> demo = Subject.register("pda_fields_demo", PdaFieldsDemo.class, () -> INSTANCE);
        Signals.text(demo, "submitted", 40, d -> submitted);
        Signals.text(demo, "live", 60, d -> "COUNT " + COUNT.text() + "  PRICE " + PRICE.text() + "  NAME " + NAME.text());
        PdaApps.register(INSTANCE, 890);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.literal("FIELDS");
    }

    @Override
    public WidgetDef build() {
        return stack(
                at("12", "12", text("COUNT 1-100", PdaShell.DIM)),
                at("12", "24", PdaTextBox.field(COUNT, "TYPE A COUNT", 100)),
                at("130", "12", text("PRICE 0-999.99", PdaShell.DIM)),
                at("130", "24", PdaTextBox.field(PRICE, "TYPE A PRICE", 110)),
                at("12", "52", text("NAME (LONG TEXT SCROLLS)", PdaShell.DIM)),
                at("12", "64", PdaTextBox.field(NAME, "TYPE A NAME", 160)),
                at("12", "96", text("{pda_fields_demo.live}", PdaShell.TEXT)),
                at("12", "110", text("{pda_fields_demo.submitted}", PdaShell.ACCENT)));
    }
}
