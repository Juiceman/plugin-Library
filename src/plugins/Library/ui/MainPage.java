package plugins.Library.ui;


import freenet.clients.http.ToadletContext;
import freenet.keys.FreenetURI;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.HTMLEncoder;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.MultiValueTable;
import freenet.support.api.HTTPRequest;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import plugins.Library.Library;
import plugins.Library.index.Request;
import plugins.Library.index.Request.RequestState;
import plugins.Library.index.URIWrapper;
import plugins.Library.search.InvalidSearchException;
import plugins.Library.search.Search;
import plugins.Library.serial.TaskAbortException;



/**
 * Generates the main search page
 *
 * @author MikeB
 */
class MainPage implements WebPage {
	private final Library library;
	private final PluginRespirator pr;

	private Search search = null;
	private ArrayList<Exception> exceptions = new ArrayList();
	private boolean showold = false;
	private boolean js = false;
	private String query = "";
	private String indexuri = Library.DEFAULT_INDEX_SITE;
	
	

	MainPage(Library library, PluginRespirator pr) {
		this.library = library;
		this.pr = pr;
	}

	/**
	 * Process a get request, the only parameters allowed for a get request are
	 * request id (for an ongoing request) and formatting parameters
	 */
	public void processGetRequest(HTTPRequest request){
		js = request.isParameterSet("js");
		showold = request.isParameterSet("showold");

		if (request.isParameterSet("request")){
			search = Search.getSearch(request.getIntParam("request"));
			query = search.getQuery();
			indexuri = search.getIndexURI();
			Logger.normal(this, "Refreshing request "+request.getIntParam("request")+" " +query);
		}
	}
	
	/**
	 * Process a post request
	 * 
	 * @see plugins.XMLSpider.WebPage#processPostRequest(freenet.support.api.HTTPRequest,
	 * freenet.support.HTMLNode)
	 */
	public void processPostRequest(HTTPRequest request, HTMLNode contentNode, boolean userAccess ) {
		js = request.isPartSet("js");
		showold = request.isPartSet("showold");


		// Get index list
		indexuri = "";
		for (String bm : library.bookmarkKeys()){
			String bmid = Library.BOOKMARK_PREFIX + bm;
			if(request.isPartSet(bmid))
				indexuri += bmid + " ";
		}
		if (request.isPartSet("index"))
			indexuri += request.getPartAsString("index", 256);
		indexuri = indexuri.trim();

		if("".equals(indexuri))
			indexuri = Library.DEFAULT_INDEX_SITE;
		
		
		// get search query
		if (request.isPartSet("search")){
			// Start or continue a search
			query = HTMLEncoder.encode(request.getPartAsString("search", 256));
			
			try {
				search = library.startSearch(query, indexuri);
			} catch (InvalidSearchException ex) {
				exceptions.add(ex);
			} catch (RuntimeException ex){
				exceptions.add(ex);
			}
		}

	}


	
	/**
	 * Write the search page out
	 *
	 * 
	 * @see plugins.XMLSpider.WebPage#writeContent(freenet.support.api.HTTPRequest,
	 * freenet.support.HTMLNode)
	 */
	public void writeContent(HTTPRequest request, HTMLNode contentNode, ToadletContext ctx, MultiValueTable<String, String> headers) {
		Logger.normal(this, "Writing page for "+ query + " " + search + " " + indexuri);
		
		// Don't refresh if there is no request option, if it is finished or there is an error to show
		if(search!=null && !"".equals(search.getQuery()) && !search.isDone() && exceptions.size()<=0){
			// refresh will GET so use a request id
			headers.put("Refresh", "1;url="+path()+"?request="+search.hashCode()+ (js ? "&js=on":"") + (showold?"&showold=on":""));
			contentNode.addChild("script", new String[]{"type", "src"}, new String[]{"text/javascript", path() + (js ? "script.js" : "detect.js")}).addChild("%", "");
		}
		
        // Start of body
		contentNode.addChild(searchBox(query, indexuri, js, showold));
		contentNode.addChild("br");


        // Show any errors
		HTMLNode errorDiv = new HTMLNode("div", "id", "errors");
		for (Exception exception : exceptions) {
			addError(errorDiv, exception);
		}
		contentNode.addChild(errorDiv);


        // If showing a search
        if(search != null){
			// show progress
			contentNode.addChild(progressBox(query, indexuri, search));
			contentNode.addChild("p");

            // If search is complete show results
            if (search.getState()==RequestState.FINISHED)
				try{
					contentNode.addChild(resultNodeGrouped(search, showold, js));
				}catch(TaskAbortException ex){
					addError(errorDiv, ex);
				}catch(RuntimeException ex){
					addError(errorDiv, ex);
				}
			else
				contentNode.addChild("div", "id", "results").addChild("#");
        }
	}


	/**
	 * Put an error on the page, under node, also draws a big grey box around the error
	 */
	public void addError(HTMLNode node, Throwable error){
		HTMLNode error1 = node.addChild("div", "style", "padding:10px;border:5px solid gray;margin:10px", error.toString());
		for (StackTraceElement ste : error.getStackTrace()){
			error1.addChild("br");
			error1.addChild("#", " -- "+ste.toString());
		}
		if(error.getCause()!=null)
			addError(error1, error.getCause());
	}


	/**
	 * Create search form
	 * @param search already started
	 * @param indexuri
	 * @param js whether js has been detected
	 * @param showold
	 * @return
	 */
	private HTMLNode searchBox(String search, String indexuri, boolean js, boolean showold){
		// Put all bookmarked indexes being used into a list and leave others in a string
		String[] indexes = indexuri.split("[ ;]");
		Set<String> allbookmarks = library.bookmarkKeys();
		ArrayList<String> usedbookmarks = new ArrayList();
		indexuri = "";
		for (String string : indexes) {
			if(string.startsWith(Library.BOOKMARK_PREFIX)
					&& allbookmarks.contains(string.substring(Library.BOOKMARK_PREFIX.length())))
				usedbookmarks.add(string);
			else
				indexuri += string + " ";
		}
		indexuri.trim();


		HTMLNode searchDiv = new HTMLNode("div", "id", "searchbar");
		HTMLNode searchForm = pr.addFormChild(searchDiv, path(), "searchform");
			HTMLNode searchTable = searchForm.addChild("table", "width", "100%");
				HTMLNode searchTop = searchTable.addChild("tr");
					HTMLNode titleCell = searchTop.addChild("td", new String[]{"rowspan","width"},new String[]{"3","120"});
						titleCell.addChild("H1", library.plugName);
					HTMLNode searchcell = searchTop.addChild("td", "width", "400");
						searchcell.addChild("input", new String[]{"name", "size", "type", "value"}, new String[]{"search", "40", "text", search});
						searchcell.addChild("input", new String[]{"name", "type", "value", "tabindex"}, new String[]{"find", "submit", "Find!", "1"});
						if(js)
							searchcell.addChild("input", new String[]{"type","name"}, new String[]{"hidden","js"});

				HTMLNode navList = searchTable.addChild("tr")
					.addChild("td")
					.addChild("div", "id", "navbar")
						.addChild("ul", "id", "navlist");
							HTMLNode newIndexInput = navList.addChild("li", new String[]{"class", "style"}, new String[]{"index", "display: inline-table;"});
								newIndexInput.addChild("input", new String[]{"type", "class"}, new String[]{"text", "index"});
								newIndexInput.addChild("br");
								newIndexInput.addChild("input", new String[]{"name", "type", "value", "class"}, new String[]{"index", "text", indexuri, "index"});
							HTMLNode subnavoptions = navList.addChild("li", "style", "display: inline-block; top: 10px; position: relative;", "Options")
								.addChild("ul", "class", "subnavlist");
									subnavoptions.addChild("li")
										.addChild("input", new String[]{"type"}, new String[]{"hidden"}, "Group SSK Editions");
									subnavoptions.addChild("li")
										.addChild("input", new String[]{"name", "type", showold?"checked":"size"}, new String[]{"showold", "checkbox", "1"}, "Show older editions");
									subnavoptions.addChild("li")
										.addChild("input", new String[]{"type"}, new String[]{"hidden"}, "Sort by relevence");
							HTMLNode entryindexes = navList.addChild("li", "style", "display: inline-block; top: 10px; position: relative;");
								entryindexes.addChild("input", "type", "submit");
								// SHows the list of bookmarked indexes TODO show descriptions on mouseover ??
								HTMLNode subnavindexes = entryindexes.addChild("ul", "class", "subnavlist", "Select indexes");
								for (String bm : library.bookmarkKeys()){
									searchDiv.addChild("%", "<!-- Checking for bm="+bm+" in \""+indexuri+"\" -->");
									subnavindexes.addChild("li")
										.addChild("input", new String[]{"type", "name", "value", (usedbookmarks.contains(Library.BOOKMARK_PREFIX+bm) ? "checked" : "size" )}, new String[]{"checkbox", Library.BOOKMARK_PREFIX+bm, Library.BOOKMARK_PREFIX+bm, "1" } , bm);
								}



						/*
					.addChild("td", L10nString.getString("Index"))
						.addChild("input", new String[]{"name", "type", "value", "size"}, new String[]{"index", "text", indexuri, "40"});
				searchTable.addChild("tr")
					.addChild("td", L10nString.getString("ShowOldVersions"))
						.addChild("input", new String[]{"name", "type", showold?"checked":"size"}, new String[]{"showold", "checkbox", showold?"checked":"1"});
						 * */
		return searchDiv;
	}


	/**
	 * Draw progress box with bars
	 * @param search
	 * @param indexuri
	 * @param request request to get progress from
	 * @return
	 */
	private static HTMLNode progressBox(String search, String indexuri, Request request){
			HTMLNode progressDiv = new HTMLNode("div", "id", "progress");
            // Search description
			HTMLNode progressTable = progressDiv.addChild("table", "width", "100%");
				HTMLNode searchingforCell = progressTable.addChild("tr")
					.addChild("td");
						searchingforCell.addChild("#", L10nString.getString("Searching-for"));
						searchingforCell.addChild("span", "class", "librarian-searching-for-target")
							.addChild("b", search);
						searchingforCell.addChild("#", L10nString.getString("in-index"));
						searchingforCell.addChild("i", indexuri);


				// Search status
				HTMLNode statusRow = progressTable.addChild("tr");
					statusRow.addChild("td")
							.addChild("div", "id", "librarian-search-status")
							.addChild("table", new String[]{"id", "class"}, new String[]{"progress-table", "progress-table"})
							.addChild(progressBar(request));
		return progressDiv;
	}

	
	/**
	 * Draw progress bars and describe progress
	 * @param request
	 * @return
	 */
	private static HTMLNode progressBar(Request request) {
		HTMLNode bar;
		// If it doesn't have subrequests, draw it's progress
		if(request.getSubRequests()==null){
			bar = new HTMLNode("tr");
			// search term
			bar.addChild("td", request.getSubject());
			// search stage
			bar.addChild("td",
				(request.getState()==RequestState.INPROGRESS ||
				 request.getState()==RequestState.PARTIALRESULT) ? request.getCurrentStage()
				                                                          : request.getState().toString());
			// show fetch progress if fetching something
			if(request.isDone() || request.partsTotal()==0){
				bar.addChild("td", ""); bar.addChild("td");
			}else{
				int percentage = (int)(100*request.partsDone()/request.partsTotal());
				boolean fetchFinalized = request.isTotalFinal();
				bar
					.addChild("td", new String[]{"class"}, new String[]{"progress-bar-outline"})
					.addChild("div", new String[]{"class", "style"}, new String[]{fetchFinalized?"progress-bar-inner-final":"progress-bar-inner-nonfinal", "z-index : -1; width:"+percentage+"%;"});
				bar.addChild("td", fetchFinalized?percentage+"%":"Fetch length unknown");

			}
		// if request separates indexes, show their names
		}else if(request.getSubject().matches(".+%.+[ ;].+")){
			bar = new HTMLNode("tbody");
			Iterator it=request.getSubRequests().iterator();
			while( it.hasNext()){
				Request r = (Request)it.next();
				HTMLNode indexrow = bar.addChild("tr");
				indexrow.addChild("td", r.getSubject().split("%")[1]);
				indexrow.addChild("td").addChild("table", "class", "progress-table").addChild(progressBar((Request)r));
			}
		// get progress for subrequests
		}else{
			bar = new HTMLNode("#");
			Iterator it=request.getSubRequests().iterator();
			while( it.hasNext()){
				Request r = (Request)it.next();
				bar.addChild(progressBar((Request)r));
			}
		}
		return bar;
	}
	

	/**
	 * Return a HTMLNode for this result
	 * @param showold whether to display results from older SSK versions
	 * @param js whether js can be used to display results
	 */
	public static HTMLNode resultNodeGrouped(Request<Collection<URIWrapper>> request, boolean showold, boolean js) throws TaskAbortException {
		// Output results
		int results = 0;
		// Loop to separate results into SSK groups
		HTMLNode resultsNode = new HTMLNode("div", "id", "results");
		HashMap<String, SortedMap<Long, Set<URIWrapper>>> groupmap = new HashMap();
		Iterator<URIWrapper> it = request.getResult().iterator();
		while(it.hasNext()){
			URIWrapper o = it.next();
			// Get the key and name
			FreenetURI uri;
			try{
				uri = new FreenetURI(o.URI);
				o.URI=uri.toString();
			}catch(MalformedURLException e){
				Logger.error(WebUI.class, "URI in results is not a Freenet URI : "+o.URI, e);
				continue;
			}
			Long uskVersion=Long.MIN_VALUE;
			// convert usk's
			if(uri.isSSKForUSK()){
				uri = uri.uskForSSK();
				// Get the USK edition
				uskVersion = uri.getEdition();
			}
			// Get the site base name, key + documentname - uskversion
			String sitebase = uri.setMetaString(null).setSuggestedEdition(0).toString().replaceFirst("/0", "");
			Logger.minor(WebUI.class, sitebase);
			// Add site
			if(!groupmap.containsKey(sitebase))
				groupmap.put(sitebase, new TreeMap<Long, Set<URIWrapper>>());
			TreeMap<Long, Set<URIWrapper>> sitemap = (TreeMap<Long, Set<URIWrapper>>)groupmap.get(sitebase);
			// Add Edition
			if(!sitemap.containsKey(uskVersion))
				sitemap.put(uskVersion, new HashSet());
			// Add page
			sitemap.get(uskVersion).add(o);
		}
		// Loop over keys
		Iterator<String> it2 = groupmap.keySet().iterator();
		while (it2.hasNext()) {
			String keybase = it2.next();
			SortedMap<Long, Set<URIWrapper>> siteMap = groupmap.get(keybase);
			HTMLNode siteNode = resultsNode.addChild("div", "style", "padding: 6px;");
			// Create a block for old versions of this SSK
			HTMLNode siteBlockOldOuter = siteNode.addChild("div", new String[]{"id", "style"}, new String[]{"result-hiddenblock-"+keybase, (!showold?"display:none":"")});
			// put title on block if it has more than one version in it
			if(siteMap.size()>1)
				siteBlockOldOuter.addChild("a", new String[]{"onClick", "name"}, new String[]{"toggleResult('"+keybase+"')", keybase}).addChild("h3", keybase.replaceAll("\\b.*/(.*)", "$1"));
			// inner block for old versions to be hidden
			HTMLNode oldEditionContainer = siteBlockOldOuter.addChild("div", new String[]{"class", "style"}, new String[]{"result-hideblock", "border-left: thick black;"});
			// Loop over all editions in this site
			Iterator<Long> it3 = siteMap.keySet().iterator();
			while(it3.hasNext()){
				Long version = it3.next();
				boolean newestVersion = !it3.hasNext();
				if(newestVersion)	// put older versions in block, newest outside block
					oldEditionContainer = siteNode;
				HTMLNode versionCell;
				HTMLNode versionNode;
				if(siteMap.get(version).size()>1||siteMap.size()>1){
					// table for this version
					versionNode = oldEditionContainer.addChild("table", new String[]{"class", "width", "border", "cellspacing", "cellpadding"}, new String[]{"librarian-result", "95%", "0px 8px", "0", "0",});
					HTMLNode grouptitle = versionNode.addChild("tr").addChild("td", new String[]{"padding", "colspan"}, new String[]{"0", "3"});
					grouptitle.addChild("h4", "style", "display:inline; padding-top: 5px; color:"+(newestVersion?"black":"darkGrey"), keybase.replaceAll("\\b.*/(.*)", "$1")+(version.longValue()>=0 ? "-"+version.toString():""));
					// Put link to show hidden older versions block if necessary
					if(newestVersion && !showold && js && siteMap.size()>1)
						grouptitle.addChild("a", new String[]{"href", "onClick"}, new String[]{"#"+keybase, "toggleResult('"+keybase+"')"}, "       ["+(siteMap.size()-1)+" older matching versions]");
					HTMLNode versionrow = versionNode.addChild("tr");
					versionrow.addChild("td", "width", "8px");
					// draw black line down the side of the version
					versionrow.addChild("td", new String[]{"bgcolor", "width"}, new String[]{"black", "2px"});

					versionCell=versionrow.addChild("td", "style", "padding-left:15px");
				}else
					versionCell = oldEditionContainer;
				// loop over each result in this version
				Iterator<URIWrapper> it4 = siteMap.get(version).iterator();
				while(it4.hasNext()){
					try {
						URIWrapper u = it4.next();
						FreenetURI uri = new FreenetURI(u.URI);
						String showtitle = u.descr;
						String showurl = uri.toShortString();
						if (showtitle.trim().length() == 0 || showtitle.equals("not available")) {
							showtitle = showurl;
						}
						String realurl = "/" + uri.toString();
						HTMLNode pageNode = versionCell.addChild("div", new String[]{"class", "style"}, new String[]{"result-title", ""});
						pageNode.addChild("a", new String[]{"href", "class", "style", "title"}, new String[]{realurl, "result-title", "color: " + (newestVersion ? "Blue" : "LightBlue"), u.URI}, showtitle);
						// create usk url
						if (uri.isSSKForUSK()) {
							String realuskurl = "/" + uri.uskForSSK().toString();
							pageNode.addChild("a", new String[]{"href", "class"}, new String[]{realuskurl, "result-uskbutton"}, "[ USK ]");
						}
						pageNode.addChild("br");
						pageNode.addChild("a", new String[]{"href", "class", "style"}, new String[]{realurl, "result-url", "color: " + (newestVersion ? "Green" : "LightGreen")}, showurl);
						results++;
					} catch (MalformedURLException ex) {
						// Invalid URL in result, maybe should display?
						Logger.normal(WebUI.class, "Invalid URL in result "+ex.toString());
					}
				}
			}
		}
		resultsNode.addChild("p").addChild("span", "class", "librarian-summary-found", "Found"+results+"results");
		return resultsNode;
    }
	
	
	
	@Override
	public WebPage clone() {
		return new MainPage(library, pr);
	}
	
	public String path() {
		return "/library/";
	}
	
	public String supportedMethods() {
		return "GET, POST";
	}

	public String name() {
		return "Search";
	}
}