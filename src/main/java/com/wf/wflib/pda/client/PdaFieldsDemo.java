package com.wf.wflib.pda.client;

import com.wf.wflib.WFLib;
import com.wf.wflib.ui.Signals;
import com.wf.wflib.ui.Subject;
import com.wf.wflib.ui.WidgetDef;
import com.wf.wflib.ui.input.TextBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import static com.wf.wflib.ui.Ui.at;
import static com.wf.wflib.ui.Ui.stack;
import static com.wf.wflib.ui.Ui.text;

/**
 * Dev-only demo of {@link PdaTextBox}: a count (1..100), a price (2 decimals, 0..999.99) and a name. Only registered with
 * {@code -Dwflib.pda.fieldsDemo=true}, so the PDA test harness can type into real fields.
 */
final class PdaFieldsDemo implements PdaApp {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "fields_demo");
    private static final PdaFieldsDemo INSTANCE = new PdaFieldsDemo();
    private static final Subject<PdaFieldsDemo> DEMO = Subject.register("pda_fields_demo", PdaFieldsDemo.class, () -> INSTANCE);
    private static String submitted = "NOTHING SUBMITTED";
    private static final TextBox COUNT = TextBox.integer(5, 1, 100).onSubmit(s -> submitted = "COUNT " + s);
    private static final TextBox PRICE = TextBox.decimal(8, 2, 0, 999.99).onSubmit(s -> submitted = "PRICE " + s);
    private static final TextBox NAME = TextBox.text(24).onSubmit(s -> submitted = "NAME " + s);

    private PdaFieldsDemo() {
    }

    static void register() {
        if (!Boolean.getBoolean("wflib.pda.fieldsDemo")) {
            return;
        }
        Signals.text(DEMO, "submitted", 40, d -> submitted);
        Signals.text(DEMO, "live", 60, d -> "COUNT " + COUNT.text() + "  PRICE " + PRICE.text() + "  NAME " + NAME.text());
        PdaApps.register(INSTANCE);
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
