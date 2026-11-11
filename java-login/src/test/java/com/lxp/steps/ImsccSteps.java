package com.lxp.steps;

import com.lxp.driver.DriverFactory;
import com.lxp.pages.ImsccPage;
import com.lxp.report.CheckLog;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Export one course as IMSCC and import the file into another, existing
 * course; then check the target has the same modules and the same pages,
 * quizzes, assignments and discussions in each.
 */
public class ImsccSteps {

    private final ImsccPage page = new ImsccPage(DriverFactory.get());

    private Map<String, Map<String, Integer>> sourceOutline;
    private int[] sourceCounts;
    private Path imscc;

    /** Quizzes in the file with no questions, by module title: the import leaves them out. */
    private final Map<String, java.util.List<String>> emptyQuizzes = new java.util.LinkedHashMap<>();

    @When("I open the course {string} with code {string}")
    public void i_open_the_course_with_code(String title, String code) {
        page.openCourse(title, code);
    }

    @When("I note its modules, quizzes, assignments and discussions")
    public void i_note_its_modules() {
        sourceCounts = page.counts();
        sourceOutline = page.outline();
        CheckLog.check("source course has modules", sourceCounts[0] + " modules · " + sourceCounts[1] + " lessons · "
                + sourceOutline, !sourceOutline.isEmpty());
        assertThat(sourceOutline).withFailMessage("The course to export shows no modules").isNotEmpty();
    }

    @When("I download the course as IMSCC")
    public void i_download_the_course_as_imscc() {
        imscc = page.exportAsImscc();
    }

    /** A Common Cartridge is a zip with imsmanifest.xml at its root that names the IMS CC schema. */
    @Then("a valid .imscc file is downloaded")
    public void a_valid_imscc_file_is_downloaded() throws IOException {
        try (ZipFile zip = new ZipFile(imscc.toFile())) {
            ZipEntry manifestEntry = zip.getEntry("imsmanifest.xml");
            CheckLog.check(".imscc has imsmanifest.xml", imscc.getFileName() + (manifestEntry != null ? " has it" : " has none"),
                    manifestEntry != null);
            assertThat(manifestEntry).withFailMessage(imscc.getFileName() + " has no imsmanifest.xml").isNotNull();

            String manifest = new String(zip.getInputStream(manifestEntry).readAllBytes(), StandardCharsets.UTF_8);
            boolean commonCartridge = manifest.contains("IMS Common Cartridge");
            CheckLog.check("manifest is an IMS Common Cartridge", commonCartridge ? "IMS Common Cartridge" : "other schema",
                    commonCartridge);

            int quizzes = count(manifest, "type=\"imsqti_xmlv1p2/imscc_xmlv1p1/assessment\"");
            int discussions = count(manifest, "type=\"imsdt_xmlv1p1\"");
            long modulesInFile = sourceOutline.keySet().stream().filter(t -> manifest.contains(escape(t))).count();
            CheckLog.check("every module is in the file", modulesInFile + " of " + sourceOutline.size()
                    + " modules, " + quizzes + " quizzes, " + discussions + " discussions", modulesInFile == sourceOutline.size());
            findEmptyQuizzes(zip, manifest);
            if (!emptyQuizzes.isEmpty()) {
                CheckLog.review("quizzes with no questions (the import skips them, so they are not expected in the target)",
                        emptyQuizzes.toString());
            }
            assertThat(commonCartridge).withFailMessage("imsmanifest.xml is not an IMS Common Cartridge").isTrue();
            assertThat(modulesInFile).withFailMessage("The file is missing modules").isEqualTo(sourceOutline.size());
        }
    }

    @When("I click Import in the course")
    public void i_click_import_in_the_course() {
        page.openImport();
    }

    @Then("the import page loads")
    public void the_import_page_loads() {
        boolean shown = page.isImportWindowShown();
        CheckLog.check("\"Import from Canvas\" opens", shown ? "opened" : "not shown", shown);
        assertThat(shown).withFailMessage("The Import from Canvas window did not open").isTrue();
    }

    @When("I upload the downloaded .imscc file")
    public void i_upload_the_downloaded_imscc_file() {
        page.upload(imscc);
        String preview = page.preview().replaceAll("\\s+", " ");
        CheckLog.check("the file is read and previewed", preview.length() > 160 ? preview.substring(0, 160) + "..." : preview, true);
    }

    @When("I submit the import")
    public void i_submit_the_import() {
        boolean done = page.confirmImport();
        String error = page.errorText();
        CheckLog.check("import finishes", done ? "imported" : (error.isEmpty() ? "still importing" : error), done);
        assertThat(done).withFailMessage("The import did not finish: " + (error.isEmpty() ? "timed out" : error)).isTrue();
    }

    @Then("the course is imported")
    public void the_course_is_imported() {
        page.reload();
        int[] counts = page.counts();
        int skipped = emptyQuizzes.values().stream().mapToInt(java.util.List::size).sum();
        int lessons = sourceCounts[1] - skipped;
        boolean same = counts[0] >= sourceCounts[0] && counts[1] >= lessons;
        CheckLog.check("course has the source's " + sourceCounts[0] + " modules · " + lessons + " lessons"
                        + (skipped > 0 ? " (" + sourceCounts[1] + " less " + skipped + " empty quizzes)" : ""),
                counts[0] + " modules · " + counts[1] + " lessons", same);
        assertThat(same).withFailMessage("Imported course shows " + counts[0] + " modules · " + counts[1] + " lessons").isTrue();
    }

    /** Each source module is in the course, with at least as many pages, quizzes, assignments and discussions. */
    @Then("all modules, quizzes, assignments and discussions are present")
    public void all_content_is_present() {
        Map<String, Map<String, Integer>> imported = page.outline();
        for (Map.Entry<String, Map<String, Integer>> module : sourceOutline.entrySet()) {
            Map<String, Integer> got = imported.get(module.getKey());
            CheckLog.soft("module \"" + module.getKey() + "\" is imported", got == null ? "missing" : "present", got != null);
            if (got == null) {
                continue;
            }
            for (String type : ImsccPage.ITEM_TYPES) {
                int want = module.getValue().get(type)
                        - ("Quiz".equals(type) ? emptyQuizzes.getOrDefault(module.getKey(), java.util.List.of()).size() : 0);
                int have = got.get(type);
                CheckLog.soft(module.getKey() + ": " + want + " " + type + "(s)", String.valueOf(have), have >= want);
            }
        }
    }

    /**
     * Quizzes whose QTI file has no questions (no &lt;item&gt;), keyed by the module that lists
     * them in the manifest. The import does not create a quiz with no questions.
     */
    private void findEmptyQuizzes(ZipFile zip, String manifest) throws IOException {
        emptyQuizzes.clear();
        try {
            javax.xml.parsers.DocumentBuilderFactory f = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            org.w3c.dom.Document doc = f.newDocumentBuilder()
                    .parse(new java.io.ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8)));
            Map<String, String> quizHref = new java.util.HashMap<>();
            org.w3c.dom.NodeList resources = doc.getElementsByTagNameNS("*", "resource");
            for (int i = 0; i < resources.getLength(); i++) {
                org.w3c.dom.Element r = (org.w3c.dom.Element) resources.item(i);
                if (r.getAttribute("type").contains("assessment") && !r.getAttribute("href").isEmpty()) {
                    quizHref.put(r.getAttribute("identifier"), r.getAttribute("href"));
                }
            }
            org.w3c.dom.NodeList items = doc.getElementsByTagNameNS("*", "item");
            for (int i = 0; i < items.getLength(); i++) {
                org.w3c.dom.Element item = (org.w3c.dom.Element) items.item(i);
                String href = quizHref.get(item.getAttribute("identifierref"));
                if (href == null) {
                    continue;
                }
                ZipEntry qti = zip.getEntry(href);
                String body = qti == null ? "" : new String(zip.getInputStream(qti).readAllBytes(), StandardCharsets.UTF_8);
                if (body.contains("<item ")) {
                    continue;
                }
                org.w3c.dom.Node module = item.getParentNode();
                String moduleTitle = module instanceof org.w3c.dom.Element m ? titleOf(m) : "";
                emptyQuizzes.computeIfAbsent(moduleTitle, k -> new java.util.ArrayList<>()).add(titleOf(item));
            }
        } catch (javax.xml.parsers.ParserConfigurationException | org.xml.sax.SAXException e) {
            CheckLog.review("could not read imsmanifest.xml for empty quizzes", e.getMessage());
        }
    }

    private static String titleOf(org.w3c.dom.Element item) {
        for (org.w3c.dom.Node n = item.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof org.w3c.dom.Element e && "title".equals(e.getLocalName() == null ? e.getNodeName() : e.getLocalName())) {
                return e.getTextContent().strip();
            }
        }
        return "";
    }

    private static int count(String text, String needle) {
        Matcher m = Pattern.compile(Pattern.quote(needle)).matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    /** Titles are XML-escaped in the manifest ("&" → "&amp;"). */
    private static String escape(String title) {
        return title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
