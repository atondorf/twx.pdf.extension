package twx.core.pdf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.io.FilenameUtils;

import com.thingworx.data.util.InfoTableInstanceFactory;
import com.thingworx.entities.utils.ThingUtilities;
import com.thingworx.logging.LogUtilities;
import com.thingworx.metadata.FieldDefinition;
import com.thingworx.metadata.annotations.ThingworxServiceDefinition;
import com.thingworx.metadata.annotations.ThingworxServiceParameter;
import com.thingworx.metadata.annotations.ThingworxServiceResult;
import com.thingworx.resources.Resource;
import com.thingworx.things.repository.FileRepositoryThing;
import com.thingworx.types.BaseTypes;
import com.thingworx.types.InfoTable;
import com.thingworx.types.collections.ValueCollection;
import com.thingworx.types.primitives.StringPrimitive;

import com.lowagie.text.Document;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfImportedPage;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.PdfCopy;
import com.microsoft.playwright.*;
import com.microsoft.playwright.Page.PdfOptions;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.Margin;
import com.microsoft.playwright.options.Media;

public class PDFExport extends Resource {

    private static Logger logger = LogUtilities.getInstance().getApplicationLogger(PDFExport.class);
    private static final long serialVersionUID = -1395344025018016841L;

    @ThingworxServiceDefinition(name = "getAvailableTimeZones", description = "", category = "", isAllowOverride = false, aspects = {"isAsync:false" })
    @ThingworxServiceResult(name = "Result", description = "", baseType = "INFOTABLE", aspects = {"isEntityDataShape:true", "dataShape:GenericStringList" })
    public InfoTable getAvailableTimeZones() throws Exception 
    {
        InfoTable it = InfoTableInstanceFactory.createInfoTableFromDataShape("GenericStringList");
        for (var id : DateTimeZone.getAvailableIDs()) {
            ValueCollection row = new ValueCollection();
            row.put("item", new StringPrimitive(id));
            it.addRow(row);
        }
        return it;
    }

    @ThingworxServiceDefinition(name = "getAvailableLocales", description = "", category = "", isAllowOverride = false, aspects = {"isAsync:false" })
    @ThingworxServiceResult(name = "Result", description = "", baseType = "INFOTABLE", aspects = {"isEntityDataShape:true", "dataShape:GenericStringList" })
    public InfoTable getAvailableLocales() throws Exception 
    {
        InfoTable it = InfoTableInstanceFactory.createInfoTableFromDataShape("GenericStringList");
        for (var id : Locale.getAvailableLocales()) {
            ValueCollection row = new ValueCollection();
            row.put("item", new StringPrimitive(id.toLanguageTag()));
            it.addRow(row);
        }
        return it;
    }

    @ThingworxServiceDefinition(name = "CreatePDF", description = "")
    public void CreatePDF(
            @ThingworxServiceParameter(name = "ServerAddress", description = "The address must be ending in /Runtime/index.html#mashup=mashup_name. It will not work with Thingworx/Mashups/mashup_name", baseType = "STRING", aspects = {""}) String url,
            @ThingworxServiceParameter(name = "AppKey", description = "AppKey", baseType = "STRING") String twAppKey,
            @ThingworxServiceParameter(name = "OutputFileName", description = "Name of the Output File without extension.", baseType = "STRING", aspects = {"defaultValue:Report" }) String OutputFileName,
            @ThingworxServiceParameter(name = "FileRepository", description = "Choose a file repository where the output file will be stored.", baseType = "THINGNAME", aspects = {"defaultValue:SystemRepository", "thingTemplate:FileRepository" }) String fileRepository,
            @ThingworxServiceParameter(name = "TimeZoneName", description = "Set a time zone to the broswer emulator. Please take a look at the GetAvailableTimezones service, to find available Timezones.", baseType = "STRING") String timeZoneName,
            @ThingworxServiceParameter(name = "LocaleName", description = "", baseType = "STRING") String localeName,
            @ThingworxServiceParameter(name = "PageFormat", description = "", baseType = "STRING", aspects = {"defaultValue:A4" }) String pageFormat,
            @ThingworxServiceParameter(name = "Landscape", description = "", baseType = "BOOLEAN", aspects = {"defaultValue:false" }) Boolean landscape,
            @ThingworxServiceParameter(name = "ScreenWidth", description = "", baseType = "INTEGER", aspects = {"defaultValue:1280" }) Integer pageWidth,
            @ThingworxServiceParameter(name = "ScreenHeight", description = "", baseType = "INTEGER", aspects = {"defaultValue:1024" }) Integer pageHeight,
            @ThingworxServiceParameter(name = "ScreenScale", description = "", baseType = "NUMBER", aspects = {"defaultValue:1.0" }) Double pageScale,
            @ThingworxServiceParameter(name = "PrintBackground", description = "", baseType = "BOOLEAN", aspects = {"defaultValue:true" }) Boolean printBackground,
            @ThingworxServiceParameter(name = "Margin", description = "", baseType = "STRING", aspects = {"defaultValue:10px" }) String margin,
            @ThingworxServiceParameter(name = "ScreenshotDelayMS", description = "Add a delay before taking the screenshot in ms", baseType = "INTEGER", aspects = {"defaultValue:0" }) Integer screenshotDelayMS)
            throws Exception 
    {
        // get the full path of the
        FileRepositoryThing filerepo = (FileRepositoryThing) ThingUtilities.findThing(fileRepository);
        filerepo.processServiceRequest("GetDirectoryStructure", null);

        // get the info of output ... 
        String outPath = FilenameUtils.getFullPath(OutputFileName);
        String outFile = FilenameUtils.getBaseName(OutputFileName);
        String outExt  = FilenameUtils.getExtension(OutputFileName);
        if( outExt.isEmpty() )
            outExt = "pdf";
        String finalPdfFilePath = filerepo.getRootPath() + File.separator + outPath + File.separator + outFile + "." + outExt;

        // default locale & timezone ...
        if (timeZoneName == "") {
            timeZoneName = TimeZone.getDefault().getID();
        }
        if (localeName == "") {
            localeName = Locale.getDefault().toLanguageTag();
        }

        try (Playwright playwright = Playwright.create()) {
            // creating the Browser ...
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setChannel("msedge")
                    .setHeadless(true));
            // creating the context ...
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setLocale(localeName)
                    .setTimezoneId(timeZoneName)
                    .setViewportSize(pageWidth, pageHeight));

            Map<String, String> headers = new HashMap<String, String>();
            headers.put("appkey", twAppKey);
            headers.put("sec-ch-ua-platform", "windows");
            headers.put("sec-ch-ua", "\"Chromium\";v=\"92\", \"Microsoft Edge\";v=\"92\", \"GREASE\";v=\"99\"");
            context.setExtraHTTPHeaders(headers);

            Page page = context.newPage();
            page.navigate(url);
            page.emulateMedia(new Page.EmulateMediaOptions().setMedia(Media.PRINT));
            page.waitForLoadState(LoadState.NETWORKIDLE);

            if (screenshotDelayMS > 0) {
                Thread.sleep(screenshotDelayMS);
            }

            page.pdf(new Page.PdfOptions()
                    .setPath(Paths.get(finalPdfFilePath))
                    .setMargin(new Margin().setTop(margin).setBottom(margin).setLeft(margin).setRight(margin))
                    .setPrintBackground(printBackground)
                    .setScale(pageScale)
                    .setFormat(pageFormat)
                    .setLandscape(landscape));
            
            browser.close();

        } catch (Exception err) {
            logger.error(err.getMessage());
        } 
    }

    @ThingworxServiceDefinition(name = "CreatePDFMultiURL", description = "Render multiple URLs to one PDF ... ")
    public void CreatePDFMultiURL(
            @ThingworxServiceParameter(name = "ServerAddresses", description = "The address must be ending in /Runtime/index.html#mashup=mashup_name. It will not work with Thingworx/Mashups/mashup_name", baseType = "INFOTABLE", aspects = {"dataShape:GenericStringList"}) InfoTable urls,
            @ThingworxServiceParameter(name = "AppKey", description = "AppKey", baseType = "STRING") String twAppKey,
            @ThingworxServiceParameter(name = "OutputFileName", description = "", baseType = "STRING", aspects = {"defaultValue:Report" }) String OutputFileName,
            @ThingworxServiceParameter(name = "FileRepository", description = "Choose a file repository where the output file will be stored.", baseType = "THINGNAME", aspects = {"defaultValue:SystemRepository", "thingTemplate:FileRepository" }) String fileRepository,
            @ThingworxServiceParameter(name = "TimeZoneName", description = "Set a time zone to the broswer emulator. Please take a look at the GetAvailableTimezones service, to find available Timezones.", baseType = "STRING") String timeZoneName,
            @ThingworxServiceParameter(name = "LocaleName", description = "", baseType = "STRING") String localeName,
            @ThingworxServiceParameter(name = "PageFormat", description = "", baseType = "STRING", aspects = {"defaultValue:A4" }) String pageFormat,
            @ThingworxServiceParameter(name = "Landscape", description = "", baseType = "BOOLEAN", aspects = {"defaultValue:false" }) Boolean landscape,
            @ThingworxServiceParameter(name = "ScreenWidth", description = "", baseType = "INTEGER", aspects = {"defaultValue:1280" }) Integer pageWidth,
            @ThingworxServiceParameter(name = "ScreenHeight", description = "", baseType = "INTEGER", aspects = {"defaultValue:1024" }) Integer pageHeight,
            @ThingworxServiceParameter(name = "ScreenScale", description = "", baseType = "NUMBER", aspects = {"defaultValue:1.0" }) Double pageScale,
            @ThingworxServiceParameter(name = "PrintBackground", description = "", baseType = "BOOLEAN", aspects = {"defaultValue:true" }) Boolean printBackground,
            @ThingworxServiceParameter(name = "Margin", description = "", baseType = "STRING", aspects = {"defaultValue:10px" }) String margin,
            @ThingworxServiceParameter(name = "ScreenshotDelayMS", description = "Add a delay before taking the screenshot in ms", baseType = "INTEGER", aspects = {"defaultValue:0" }) Integer screenshotDelayMS,
            @ThingworxServiceParameter(name = "KeepTemp", description = "", baseType = "BOOLEAN", aspects = {"defaultValue:false" }) Boolean keepTemp)
            throws Exception 
    {
        // get the full path of the
        FileRepositoryThing filerepo = (FileRepositoryThing) ThingUtilities.findThing(fileRepository);
        filerepo.processServiceRequest("GetDirectoryStructure", null);

        // get the info of output ... 
        String outPath = FilenameUtils.getFullPath(OutputFileName);
        String outFile = FilenameUtils.getBaseName(OutputFileName);
        String outExt  = FilenameUtils.getExtension(OutputFileName);
        if( outExt.isEmpty() )
            outExt = "pdf";

        String finalPdfFilePath = outPath + File.separator + outFile + "." + outExt;    //<< here it must be relative to repository ... 
        String tempPdfFolderPath = outPath + File.separator +  "_" + outFile;
        // get the full path of the
        filerepo.CreateFolder(tempPdfFolderPath);

        // default locale & timezone ...
        if (timeZoneName == "") {
            timeZoneName = TimeZone.getDefault().getID();
        }
        if (localeName == "") {
            localeName = Locale.getDefault().toLanguageTag();
        }

        try (Playwright playwright = Playwright.create()) {
            // creating the Browser ...
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setChannel("msedge")
                    .setHeadless(true));
            // creating the context ...
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setLocale(localeName)
                    .setTimezoneId(timeZoneName)
                    .setViewportSize(pageWidth, pageHeight));

            Map<String, String> headers = new HashMap<String, String>();
            headers.put("appkey", twAppKey);
            headers.put("sec-ch-ua-platform", "windows");
            headers.put("sec-ch-ua", "\"Chromium\";v=\"92\", \"Microsoft Edge\";v=\"92\", \"GREASE\";v=\"99\"");
            context.setExtraHTTPHeaders(headers);

            // create a temp folder for the pdfs ... 
            InfoTable pdfFiles = new InfoTable();
            pdfFiles.addField(new FieldDefinition("item", BaseTypes.STRING));

            Page page = context.newPage();
            Integer pdfId = 0;
            for (ValueCollection row : urls.getRows() ) {
                pdfId++;
                String url = row.getStringValue("item");
                String filePath = filerepo.getRootPath() + File.separator + tempPdfFolderPath + File.separator + pdfId + "." + outExt;

                // store temp file for merge access ...
                var pdfFile = new ValueCollection();
                pdfFile.SetStringValue("item", tempPdfFolderPath + File.separator + pdfId + "." + outExt );   // must be relative to repos ... 
                pdfFiles.addRow(pdfFile);

                // navigate and add render PDF ... 
                page.navigate(url);
                page.emulateMedia(new Page.EmulateMediaOptions().setMedia(Media.PRINT));
                page.waitForLoadState(LoadState.NETWORKIDLE);

                if (screenshotDelayMS > 0) {
                    Thread.sleep(screenshotDelayMS);
                }

                page.pdf(new Page.PdfOptions()
                        .setPath( Paths.get(filePath) )
                        .setMargin(new Margin().setTop(margin).setBottom(margin).setLeft(margin).setRight(margin))
                        .setPrintBackground(printBackground)
                        .setScale(pageScale)
                        .setFormat(pageFormat)
                        .setLandscape(landscape));
            }
            browser.close();

            // merge the pdf files ... 
            this.MergePDFs(pdfFiles, finalPdfFilePath, fileRepository);

            Thread.sleep(100);

            // finally delet the temp files ... 
            if( !keepTemp ) {
                filerepo.DeleteFolder(tempPdfFolderPath);
            }

        } catch (Exception err) {
            logger.error(err.getMessage());
        } 
    }

    @ThingworxServiceDefinition(name = "MergePDFs", description = "Takes an InfoTable of PDF filenames in the given FileRepository and merges them into a single PDF.")
    @ThingworxServiceResult(name = "Result", description = "Contains error message in case of error.", baseType = "STRING")
    public String MergePDFs(
            @ThingworxServiceParameter(name = "Filenames", description = "List of PDF filenames to merge together.", baseType = "INFOTABLE", aspects = {"dataShape:GenericStringList" }) InfoTable filenames,
            @ThingworxServiceParameter(name = "OutputFileName", description = "Name of the Output File without extension.", baseType = "STRING") String OutputFileName,
            @ThingworxServiceParameter(name = "FileRepository", description = "The name of the file repository to use.", baseType = "THINGNAME", aspects = {"defaultValue:SystemRepository", "thingTemplate:FileRepository" }) String FileRepository) 
            throws Exception
    {
        FileRepositoryThing filerepo = (FileRepositoryThing) ThingUtilities.findThing(FileRepository);
        filerepo.processServiceRequest("GetDirectoryStructure", null);

        // get the info of output ... 
        String outPath = FilenameUtils.getFullPath(OutputFileName);
        String outFile = FilenameUtils.getBaseName(OutputFileName);
        String outExt  = FilenameUtils.getExtension(OutputFileName);
        if( outExt.isEmpty() )
            outExt = "pdf";
        String finalPdfFilePath = filerepo.getRootPath() + File.separator + outPath + File.separator + outFile + "." + outExt;

        // Get the File Repository
        String str_Result = "Success";
        Document document = null;
        OutputStream out = null;
        PdfCopy writer = null;
        try {
            // Loop through the given PDFs that will be merged
            int f = 0;
            for (ValueCollection row : filenames.getRows()) {
                String inputPdfFilePath = filerepo.getRootPath() + File.separator + row.getStringValue("item");
                try ( 
                    InputStream is = new FileInputStream(new File(inputPdfFilePath));
                    PdfReader reader = new PdfReader(is);
                ) {
                    int n = reader.getNumberOfPages();
                    if (f == 0) {
                        // step 1: creation of a document-object
                        document = new Document(reader.getPageSizeWithRotation(1));
                        // step 2: we create a writer that listens to the document
                        writer = new PdfCopy(document, new FileOutputStream(finalPdfFilePath));
                        // step 3: we open the document
                        document.open();
                    }
                    // step 4: we add content
                    PdfImportedPage page;
                    for (int i = 0; i < n;) {
                        ++i;
                        page = writer.getImportedPage(reader, i);
                        writer.addPage(page);
                    }
                    f++;
                }
            }
        } catch (FileNotFoundException e) {
            str_Result = "Unable to create output file. Exception-Message: " + e.getMessage();
            logger.error(str_Result, e);
        } catch (Exception e) {
            str_Result = "Unable to Get Directory Structure of File Repository: " + FileRepository + " Exception-Message: " +  e.getMessage();
            logger.error(str_Result, e);
        } 
        finally {
            try {
            // step 5: we close the document
            if (document != null)
                document.close();
                if( out != null )
                    out.close();
                if( writer != null )
                    writer.close();
            }
            catch (Exception e) {
                str_Result = "Caught exception on close! Exception-Message: " +  e.getMessage();
            logger.error(str_Result, e);
            }   
        } 
        return str_Result;
    }
}
