package com.wf.wflib;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.wf.wflib", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    private static final String ROOT = "com.wf.wflib.";

    /** Edges guarded by {@code WFSystems.enabled} at the call site; system -> systems it may touch without requiring. */
    private static final Map<String, Set<String>> GUARDED = Map.ofEntries(
            Map.entry("missile", Set.of("probe")),
            Map.entry("laser", Set.of("nvg")),
            Map.entry("damage", Set.of("armor")),
            Map.entry("mist", Set.of("armor")),
            Map.entry("view", Set.of("armor")),
            Map.entry("round", Set.of("armor")),
            Map.entry("armor", Set.of("nvg", "skin")),
            Map.entry("backpack", Set.of("probe")),
            Map.entry("hazard", Set.of("armor")),
            Map.entry("radiation", Set.of("hazard")),
            Map.entry("anomaly", Set.of("hazard")),
            Map.entry("artifact", Set.of("hazard")),
            Map.entry("colony", Set.of("mine", "industry")),
            Map.entry("drone", Set.of("build", "mine")),
            Map.entry("mine", Set.of("probe")),
            Map.entry("compat", Set.of("armor", "artifact", "backpack", "carry", "magazine", "mine")));

    /** Packages that are systems without a {@link WFSystem} entry; all mandatory. */
    private static final Set<String> BARE_MANDATORY = Set.of("core", "geom", "testkit", "recoil");

    @ArchTest
    static final ArchRule SYSTEM_DEPENDENCIES_DECLARED =
            classes().that(new DescribedPredicate<JavaClass>("belong to a system") {
                @Override
                public boolean test(JavaClass c) {
                    return owner(c, Systems.INSTANCE.ids) != null && !c.getPackageName().contains(".gametest");
                }
            }).should(new ArchCondition<JavaClass>("depend on other systems only through requires(), mandatory systems or guards") {
                @Override
                public void check(JavaClass c, ConditionEvents events) {
                    Systems s = Systems.INSTANCE;
                    String from = owner(c, s.ids);
                    if (from.equals("core")) {
                        return;
                    }
                    for (Dependency d : c.getDirectDependenciesFromSelf()) {
                        String to = owner(d.getTargetClass(), s.ids);
                        if (to == null || to.equals(from) || s.mandatory.contains(to)
                                || s.requires.getOrDefault(from, Set.of()).contains(to)
                                || GUARDED.getOrDefault(from, Set.of()).contains(to)) {
                            continue;
                        }
                        events.add(SimpleConditionEvent.violated(c, "Class <" + c.getName() + "> of system '" + from
                                + "' depends on <" + d.getTargetClass().getName() + "> of system '" + to + "'"));
                    }
                }
            });

    /** R1/R2 inside wflib: a system reaches another only through its {@code api}. */
    @ArchTest
    static final ArchRule OTHER_SYSTEMS_THROUGH_API =
            classes().that(new DescribedPredicate<JavaClass>("belong to a system other than core, outside gametests") {
                @Override
                public boolean test(JavaClass c) {
                    String o = owner(c, Systems.INSTANCE.ids);
                    return o != null && !o.equals("core") && !c.getPackageName().contains(".gametest");
                }
            }).should(new ArchCondition<JavaClass>("reach other systems through their api only") {
                @Override
                public void check(JavaClass c, ConditionEvents events) {
                    String from = owner(c, Systems.INSTANCE.ids);
                    for (Dependency d : c.getDirectDependenciesFromSelf()) {
                        JavaClass t = d.getTargetClass();
                        String to = owner(t, Systems.INSTANCE.ids);
                        if (to == null || to.equals(from) || t.getPackageName().startsWith(ROOT + to + ".api")
                                || isActiveGuard(d, t, to)) {
                            continue;
                        }
                        events.add(SimpleConditionEvent.violated(c, c.getName() + " -> " + t.getName()));
                    }
                }
            });

    /** Present only on a client or with an optional mod; loading a system entry class must not need them. */
    private static final String[] CLIENT_OR_OPTIONAL = {
            "net.minecraft.client..", "dev.engine_room.flywheel..", "com.wf.gemrender..", "xaero..", "journeymap..",
            "net.irisshaders..", "com.gregtechceu..", "top.theillusivec4.curios.."};

    @ArchTest
    static final ArchRule SYSTEM_ENTRIES_LOAD_ON_A_BARE_SERVER = noClasses()
            .that().implement(com.wf.wflib.core.api.WFSystem.class)
            .should().dependOnClassesThat().resideInAnyPackage(CLIENT_OR_OPTIONAL)
            .allowEmptyShould(true);

    /** A switchable system's listeners run only while it is on, so they register inside {@code register}. */
    @ArchTest
    static final ArchRule SWITCHABLE_SYSTEMS_DO_NOT_AUTO_SUBSCRIBE = classes()
            .that(new DescribedPredicate<JavaClass>("belong to a switchable system") {
                @Override
                public boolean test(JavaClass c) {
                    Systems s = Systems.INSTANCE;
                    String sys = owner(c, s.ids);
                    return sys != null && !s.mandatory.contains(sys);
                }
            })
            .should().notBeAnnotatedWith(net.neoforged.fml.common.EventBusSubscriber.class)
            .allowEmptyShould(true);

    /** R5: shared mutable state goes through a Hook, a registry or an accessor. */
    @ArchTest
    static final ArchRule NO_PUBLIC_STATIC_NON_FINAL_FIELDS = com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields()
            .that().arePublic().and().areStatic().and().areNotFinal()
            .should().bePrivate()
            .allowEmptyShould(true);

    /** R4: {@code *Registry} names the two extension-point types only; saved data is {@code *Data}. */
    @ArchTest
    static final ArchRule REGISTRY_NAME_IS_RESERVED = noClasses()
            .that().haveSimpleNameEndingWith("Registry")
            .and().doNotHaveFullyQualifiedName(com.wf.wflib.core.api.WFRegistry.class.getName())
            .and().doNotHaveFullyQualifiedName(com.wf.wflib.core.api.OrderedRegistry.class.getName())
            .should().bePublic()
            .allowEmptyShould(true);

    /** A mandatory system's {@code requires()} names mandatory systems only; else the target is silently mandatory. */
    @org.junit.jupiter.api.Test
    void mandatorySystemsRequireOnlyMandatorySystems() {
        Systems s = Systems.INSTANCE;
        for (String sys : s.mandatory) {
            for (String need : s.requires.getOrDefault(sys, Set.of())) {
                org.junit.jupiter.api.Assertions.assertTrue(s.mandatory.contains(need),
                        "mandatory '" + sys + "' requires switchable '" + need + "'");
            }
        }
    }

    /** A mutual {@code requires()} pair among switchable systems is one switch: merge or invert. */
    @org.junit.jupiter.api.Test
    void switchableRequiresAreAcyclic() {
        Systems s = Systems.INSTANCE;
        for (String root : s.ids) {
            if (s.mandatory.contains(root)) {
                continue;
            }
            Set<String> seen = new HashSet<>();
            java.util.ArrayDeque<String> work = new java.util.ArrayDeque<>(s.requires.getOrDefault(root, Set.of()));
            while (!work.isEmpty()) {
                String next = work.poll();
                if (s.mandatory.contains(next) || !seen.add(next)) {
                    continue;
                }
                org.junit.jupiter.api.Assertions.assertNotEquals(root, next, "requires() cycle through " + root);
                work.addAll(s.requires.getOrDefault(next, Set.of()));
            }
        }
    }

    /** Every {@link #GUARDED} entry has a live {@code WFSystems.enabled("<target>")} call in the guarding system. */
    @org.junit.jupiter.api.Test
    void guardedEdgesHaveALiveGuard() throws IOException {
        for (Map.Entry<String, Set<String>> entry : GUARDED.entrySet()) {
            Path dir = Path.of("src/main/java/com/wf/wflib", entry.getKey());
            StringBuilder text = new StringBuilder();
            try (var files = Files.walk(dir)) {
                for (Path f : (Iterable<Path>) files.filter(x -> x.toString().endsWith(".java"))::iterator) {
                    text.append(Files.readString(f));
                }
            }
            for (String target : entry.getValue()) {
                String guard = Character.toUpperCase(target.charAt(0)) + target.substring(1) + "System.active()";
                org.junit.jupiter.api.Assertions.assertTrue(
                        text.toString().contains("enabled(\"" + target + "\")")
                                || text.toString().contains("enabled(\"wflib:" + target + "\")")
                                || text.toString().contains(guard),
                        entry.getKey() + " guards " + target + " with no WFSystems.enabled call");
            }
        }
    }

    /** R9: no shipped text names a dependent mod. */
    @org.junit.jupiter.api.Test
    void sourcesNameNoDependent() throws IOException {
        Pattern names = Pattern.compile("combinedassault|mineassault|wfmedical|wfcore|ywzj|"
                + "\\b(CombinedAssault|MineAssault|WFMedical)\\b", Pattern.CASE_INSENSITIVE);
        java.util.List<String> hits = new java.util.ArrayList<>();
        for (String root : new String[] {"src/main/java", "src/test/java", "src/main/resources"}) {
            try (var files = Files.walk(Path.of(root))) {
                for (Path f : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                    String name = f.getFileName().toString();
                    if (!(name.endsWith(".java") || name.endsWith(".json") || name.endsWith(".toml")
                            || name.endsWith(".mcmeta") || name.endsWith(".lang") || name.endsWith(".cfg"))
                            || f.toString().endsWith("ArchitectureTest.java")) {
                        continue;
                    }
                    int line = 0;
                    for (String l : Files.readAllLines(f)) {
                        line++;
                        if (names.matcher(l).find()) {
                            hits.add(f + ":" + line);
                        }
                    }
                }
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(hits.isEmpty(), "names a dependent: " + hits);
    }

    /** K-b: {@code <Sys>System.active()} is the guard a hot path reads instead of {@code WFSystems.enabled}. */
    private static boolean isActiveGuard(Dependency d, JavaClass target, String sys) {
        return target.getName().equals(ROOT + sys + "." + Character.toUpperCase(sys.charAt(0)) + sys.substring(1) + "System")
                && d.getDescription().contains(target.getName() + ".active()");
    }

    private static String owner(JavaClass c, Set<String> ids) {
        String pkg = c.getPackageName();
        if (!pkg.startsWith(ROOT)) {
            return null;
        }
        String[] parts = pkg.substring(ROOT.length()).split("\\.");
        String head = parts[0].equals("mixin") && parts.length > 1 ? parts[1] : parts[0];
        return ids.contains(head) ? head : null;
    }

    /** Read from source: system classes cannot initialise without a Minecraft bootstrap. */
    private static final class Systems {
        static final Systems INSTANCE = new Systems();

        final Set<String> ids = new HashSet<>(BARE_MANDATORY);
        final Set<String> mandatory = new HashSet<>(BARE_MANDATORY);
        final Map<String, Set<String>> requires = new HashMap<>();

        private Systems() {
            Pattern id = Pattern.compile("public String id\\(\\) \\{\\s*return \"(\\w+)\";");
            Pattern mand = Pattern.compile("public boolean mandatory\\(\\) \\{\\s*return true;");
            Pattern req = Pattern.compile("requires\\(\\) \\{\\s*return (?:java\\.util\\.)?Set\\.of\\(([^)]*)\\);");
            try (var files = Files.walk(Path.of("src/main/java/com/wf/wflib"))) {
                for (Path file : (Iterable<Path>) files.filter(f -> f.getFileName().toString().endsWith("System.java"))::iterator) {
                    String text = Files.readString(file);
                    Matcher m = id.matcher(text);
                    if (!text.contains("implements WFSystem") || !m.find()) {
                        continue;
                    }
                    String sys = m.group(1);
                    ids.add(sys);
                    if (mand.matcher(text).find()) {
                        mandatory.add(sys);
                    }
                    Set<String> needs = new HashSet<>();
                    Matcher r = req.matcher(text);
                    if (r.find()) {
                        for (String n : r.group(1).split(",")) {
                            if (!n.isBlank()) {
                            needs.add(n.trim().replace("\"", ""));
                        }
                        }
                    }
                    requires.put(sys, needs);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
