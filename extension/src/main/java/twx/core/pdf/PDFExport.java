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

import com.lowagie.text.Document;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfImportedPage;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;
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
			@ThingworxServiceParameter(name = "PageFormat", description = "", baseType = "STRING", aspects = {"defaultValue:A4" }) String pageFormat,
			@ThingworxServiceParameter(name = "Landscape", description = "", baseType = "BOOLEAN", aspects = {"defaultValue:false" }) Boolean landscape,
            @ThingworxServiceParameter(name = "ScreenWidth", description = "", baseType = "INTEGER", aspects = {"defaultValue:1280" }) Integer pageWidth,
            @ThingworxServiceParameter(name = "ScreenHeight", description = "", baseType = "INTEGER", aspects = {"defaultValue:1024" }) Integer pageHeight,
            @ThingworxServiceParameter(name = "ScreenScale", description = "", baseType = "NUMBER", aspects = {"defaultValue:1.0" }) Double pageScale,
			@ThingworxServiceParameter(name = "PrintBackground", description = "", baseType = "BOOLEAN", aspects = {"defaultValue:true" }) Boolean printBackground,            
			@ThingworxServiceParameter(name = "Margin", description = "", baseType = "STRING") String margin
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
        if( margin == "" ) {
            margin = "10px";
        }
        this.renderPDF(url, twAppKey, filePath, localeName, timeZoneName, pageWidth, pageHeight, pageScale, margin, pageFormat, landscape, printBackground );
    }

    public void renderPDF(String url, String appKey, String filePath, String localeName, String timeZoneName, Integer pageWidth, Integer pageHeight, double pageScale, String margin, String pageFormat, Boolean landscape, Boolean printBackground ) {
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
                .setViewportSize(pageWidth, pageHeight )
            );

            Map<String, String> headers = new HashMap<String, String>();
            headers.put("appkey", appKey);
            headers.put("sec-ch-ua-platform", "windows"); 
            headers.put("sec-ch-ua", "\"Chromium\";v=\"92\", \"Microsoft Edge\";v=\"92\", \"GREASE\";v=\"99\""); 
            context.setExtraHTTPHeaders(headers);

            Page page = context.newPage();            
            page.navigate(url);
            page.emulateMedia(new Page.EmulateMediaOptions().setMedia(Media.PRINT));            
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.pdf( new Page.PdfOptions()
                .setPath( Paths.get(filePath) )
                .setMargin( new Margin().setTop(margin).setBottom(margin).setLeft(margin).setRight(margin))
                .setPrintBackground(printBackground)
                .setScale(pageScale)
                .setFormat(pageFormat)
                .setLandscape(landscape)
            );
            browser.close();
        }
        catch(Exception err) {
            logger.error( err.getMessage() );
        }
    }

    @ThingworxServiceDefinition(name = "MergePDFs", description = "Takes an InfoTable of PDF filenames in the given FileRepository and merges them into a single PDF.")
	@ThingworxServiceResult(name = "Result", description = "Contains error message in case of error.", baseType = "STRING")
	public String MergePDFs(
			@ThingworxServiceParameter(name = "Filenames", description = "List of PDF filenames to merge together.", baseType = "INFOTABLE", aspects = {"dataShape:GenericStringList"}) InfoTable filenames, 
			@ThingworxServiceParameter(name = "OutputFileName", description = "Name of the merged PDF.", baseType = "STRING") String OutputFileName, 
			@ThingworxServiceParameter(name = "FileRepository", description = "The name of the file repository to use.", baseType = "THINGNAME", aspects = {"defaultValue:SystemRepository", "thingTemplate:FileRepository"}) String FileRepository
	)
	{
		String str_Result = "Success";
		Document document = null;
		OutputStream out = null;
		
		//Get the File Repository
		FileRepositoryThing filerepo = (FileRepositoryThing) ThingUtilities.findThing(FileRepository);	
		try 
		{
			filerepo.processServiceRequest("GetDirectoryStructure", null);
			//Set up the output stream for the merged PDF
			out = new FileOutputStream(new File(filerepo.getRootPath() + File.separator + OutputFileName));
			document = new Document(PageSize.A4, 10, 10, 10, 10);
			PdfWriter writer = PdfWriter.getInstance(document, out);
			document.open();
			PdfContentByte cb = writer.getDirectContent();
			//Loop through the given PDFs that will be merged
			for(ValueCollection row : filenames.getRows())
	        {
	            String filename = row.getStringValue("item");
	            InputStream is = new FileInputStream(new File(filerepo.getRootPath() + File.separator + filename));
	            PdfReader reader = new PdfReader(is);
	            //Write the pages from the to-be-merged PDFs to the Output PDF
	            for(int i = 1; i <= reader.getNumberOfPages(); i++)
	            {
	            	document.newPage();
	            	PdfImportedPage page = writer.getImportedPage(reader, i);
	            	cb.addTemplate(page, 0, 0);
	            }
	        }	
		} 
		catch (FileNotFoundException e) 
		{
			str_Result = "Unable to create output file.";
			logger.error(str_Result, e);
		} 
		catch (Exception e) 
		{
			str_Result = "Unable to Get Directory Structure of File Repository: " + FileRepository;
			logger.error(str_Result, e);
		}
		finally
		{
			try
			{
				//close all the output streams
				out.flush();
				document.close();
				out.close();
			}
			catch (IOException e)
			{
				str_Result = "Unable to write PDF and close OutputStreams.";
				logger.error(str_Result, e);
			}
		}
		return str_Result;	
	}

}
