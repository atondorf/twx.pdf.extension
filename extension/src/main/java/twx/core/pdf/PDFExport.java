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

import com.thingworx.data.util.InfoTableInstanceFactory;
import com.thingworx.entities.utils.ThingUtilities;
import com.thingworx.logging.LogUtilities;
import com.thingworx.metadata.annotations.ThingworxServiceDefinition;
import com.thingworx.metadata.annotations.ThingworxServiceParameter;
import com.thingworx.metadata.annotations.ThingworxServiceResult;
import com.thingworx.resources.Resource;
import com.thingworx.things.repository.FileRepositoryThing;
import com.thingworx.types.InfoTable;
import com.thingworx.types.collections.ValueCollection;
import com.thingworx.types.primitives.StringPrimitive;

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
    public InfoTable getAvailableTimeZones() throws Exception {
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
    public InfoTable getAvailableLocales() throws Exception {
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
            @ThingworxServiceParameter(name = "ServerAddress", description = "The address must be ending in /Runtime/index.html#mashup=mashup_name. It will not work with Thingworx/Mashups/mashup_name", baseType = "STRING", aspects = {""} ) String url,
            @ThingworxServiceParameter(name = "AppKey", description = "AppKey", baseType = "STRING") String twAppKey,
			@ThingworxServiceParameter(name = "OutputFileName", description = "", baseType = "STRING",aspects = {"defaultValue:Report.pdf" }) String fileName,
			@ThingworxServiceParameter(name = "FileRepository", description = "Choose a file repository where the output file will be stored.", baseType = "THINGNAME", aspects = {"defaultValue:SystemRepository", "thingTemplate:FileRepository" }) String fileRepository,
			@ThingworxServiceParameter(name = "TimeZoneName", description = "Set a time zone to the broswer emulator. Please take a look at the GetAvailableTimezones service, to find available Timezones.", baseType = "STRING") String timeZoneName,
			@ThingworxServiceParameter(name = "LocaleName", description = "", baseType = "STRING") String localeName,
            @ThingworxServiceParameter(name = "ScreenWidth", description = "", baseType = "INTEGER", aspects = {"defaultValue:1024" }) Integer pageWidth,
            @ThingworxServiceParameter(name = "ScreenScale", description = "", baseType = "NUMBER", aspects = {"defaultValue:1.0" }) Double pageScale
	) throws Exception {
        // get the full path of the 
        FileRepositoryThing filerepo = (FileRepositoryThing) ThingUtilities.findThing(fileRepository);
        filerepo.processServiceRequest("GetDirectoryStructure", null);
        String filePath = filerepo.getRootPath() + File.separator + fileName;
        // default locale & timezone ... 
        if( timeZoneName == "" ) { 
            timeZoneName = TimeZone.getDefault().getID();          
        }
        if( localeName == "" ) {
            localeName = Locale.getDefault().toLanguageTag();
        }
        this.renderPDF(url, twAppKey, filePath, localeName, timeZoneName, pageWidth, pageScale );
    }

    public void renderPDF(String url, String appKey, String filePath, String localeName, String timeZoneName, Integer pageWidth, double pageScale ) {
        try ( Playwright playwright = Playwright.create() ) {
            // creating the Browser ... 
            Browser browser = playwright.chromium().launch( new BrowserType.LaunchOptions()
                .setChannel("msedge")
                .setHeadless(true) 
            );
            // creating the context ...
            BrowserContext  context = browser.newContext( new Browser.NewContextOptions()
                .setLocale(localeName)
                .setTimezoneId(timeZoneName)
                .setViewportSize(pageWidth, 800 )
            );

            Map<String, String> headers = new HashMap<String, String>();
            headers.put("appkey", appKey);
            context.setExtraHTTPHeaders(headers);

            Page page = context.newPage();
            
            page.navigate(url);
            page.emulateMedia(new Page.EmulateMediaOptions().setMedia(Media.PRINT));            
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.pdf( new Page.PdfOptions()
                .setPath( Paths.get(filePath) )
                .setPrintBackground(true)
                .setScale(pageScale)
                .setLandscape(false)
                .setFormat("A4")
            );
            browser.close();
        }
        catch(Exception err) {
            logger.error( err.getMessage() );
        }
    }

}
