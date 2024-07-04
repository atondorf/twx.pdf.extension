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
import org.json.JSONArray;
import org.json.JSONObject;
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
import com.thingworx.types.primitives.DatetimePrimitive;
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
    @ThingworxServiceResult(name = "Result", description = "", baseType = "JSON", aspects = {})
    public JSONObject CreatePDF(
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
        // prepare Result ... 
        JSONObject result       = new JSONObject();
        JSONArray  renderLog    = new JSONArray();
        result.put("Log", renderLog);

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

            // increase Timeout ... 
            context.setDefaultTimeout(120000);

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
        return result;
    }

    @ThingworxServiceDefinition(name = "CreatePDFMultiURL", description = "Render multiple URLs to one PDF ... ")
    @ThingworxServiceResult(name = "Result", description = "", baseType = "JSON", aspects = {})
    public JSONObject CreatePDFMultiURL(
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
            @ThingworxServiceParameter(name = "KeepTempPDFs", description = "", baseType = "BOOLEAN", aspects = {"defaultValue:false" }) Boolean keepTemp)
            throws Exception 
    {
        var ts_start = new DateTime();

        JSONObject result       = new JSONObject();
        JSONArray  renderLog    = new JSONArray();
        result.put("Logging", renderLog);

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
        
        // logging in result JSON ... 
        result.put( "finalPdfFilePath", finalPdfFilePath );
        result.put( "tempPdfFolderPath", tempPdfFolderPath );
        result.put( "timeZoneName", timeZoneName );
        result.put( "localeName", localeName );
        renderLog.put( this.createLogEntry("Creating Playwright Interface") );

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

            // increase Timeout ... 
            context.setDefaultTimeout(120000);

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

            // logging in result JSON ... 
            renderLog.put( this.createLogEntry("Browser Started!") );

            for (ValueCollection row : urls.getRows() ) {
                pdfId++;
                String url = row.getStringValue("item");
                String filePath = filerepo.getRootPath() + File.separator + tempPdfFolderPath + File.separator + pdfId + "." + outExt;
            
                // logging in result JSON ... 
                renderLog.put( this.createLogEntry("ID: " + pdfId.toString() + " Browsing URL: " + url) );

                try {
                    // navigate and add render PDF ... 
                    Response response = page.navigate(url);
                    if( response != null && response.ok() ) {
                        page.emulateMedia(new Page.EmulateMediaOptions().setMedia(Media.PRINT));
                        page.waitForLoadState(LoadState.NETWORKIDLE);

                        if (screenshotDelayMS > 0) {
                            Thread.sleep(screenshotDelayMS);
                        }

                        // logging in result JSON ... 
                        renderLog.put( this.createLogEntry("ID: " + pdfId.toString() + " Render PDF to: " + tempPdfFolderPath + File.separator + pdfId + "." + outExt ) );

                        byte[] pdfBytes = page.pdf(new Page.PdfOptions()
                                .setPath( Paths.get(filePath) )
                                .setMargin(new Margin().setTop(margin).setBottom(margin).setLeft(margin).setRight(margin))
                                .setPrintBackground(printBackground)
                                .setScale(pageScale)
                                .setFormat(pageFormat)
                                .setLandscape(landscape));
                        
                        if( pdfBytes != null ) {
                            // store temp file for merge access ...
                            var pdfFile = new ValueCollection();
                            pdfFile.SetStringValue("item", tempPdfFolderPath + File.separator + pdfId + "." + outExt );   // must be relative to repos ... 
                            pdfFiles.addRow(pdfFile);
                        } else {
                            logger.error("Unable to create PDF is empty" );
                            renderLog.put( this.createLogEntry("ID: " + pdfId.toString() + " Unable to create PDF is empty") );
                        }
                    } else {
                        logger.error("Bad response from page url: {}", url );
                        renderLog.put( this.createLogEntry("ID: " + pdfId.toString() + " Bad response from page") );                        
                    }
                    // logging in result JSON ... 
                    renderLog.put( this.createLogEntry("ID: " + pdfId.toString() + " Finished ") );
                }
                catch(PlaywrightException ex) {
                    logger.error("Caught exception rendering url: {} - Exception: {}", url, ex );
                    // logging in result JSON ... 
                    renderLog.put( this.createLogEntry("Caught Error: " + ex.toString() ) );
                }
            }
            // logging in result JSON ... 
            renderLog.put( this.createLogEntry("All URLs rendered, closing browser") );

            browser.close();

            renderLog.put( this.createLogEntry("Merge PDFs started") );

            // merge the pdf files ... 
            this.MergePDFs(pdfFiles, finalPdfFilePath, fileRepository);

            Thread.sleep(100);

            renderLog.put( this.createLogEntry("Delete Temporary folder") );

            // finally delet the temp files ... 
            if( !keepTemp ) {
                filerepo.DeleteFolder(tempPdfFolderPath);
            }
        } catch (Exception err) {
            renderLog.put( this.createLogEntry("Caught Error: " + err.getMessage() ) );
            logger.error(err.getMessage());
        } 
        // final logging ... 
        long elapsed = new DateTime().getMillis() - ts_start.getMillis();
        renderLog.put( this.createLogEntry("PDF Rendering Finished!") );
        result.put( "elapsed_ms", elapsed );
        return result;
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

    protected String createLogEntry( String text ) {
        DateTimeZone tz = DateTimeZone.getDefault();
        String ts = new DateTime().withZone(tz).toString();
        return ts + " -  " + text;
    }
}
