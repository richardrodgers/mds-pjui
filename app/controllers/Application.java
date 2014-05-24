package controllers;

import play.*;
import play.cache.Cache;
import play.mvc.*;

import views.html.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dspace.content.Bitstream;
import org.dspace.content.BoundedIterator;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.handle.HandleManager;
import org.dspace.mxres.MetadataView;
import org.dspace.mxres.ResourceMap;
import org.dspace.search.Harvest;
import org.dspace.search.HarvestedItemInfo;
import org.dspace.search.DSQuery;
import org.dspace.search.QueryArgs;
import org.dspace.search.QueryResults;

import static org.dspace.core.Constants.*;

public class Application extends Controller {
    
    private static final int browseSize = 4;
    private static MetadataView listView;

    public static Result index() {
        Result result = (Result)Cache.get("home");
        if (result == null) {
            try (Context context = new Context()) {
                List<Community> topComms = new ArrayList<>();
                BoundedIterator<Community> topIter = Community.findAllTop(context);
                while (topIter.hasNext()) {
                    topComms.add(topIter.next());
                }
                result = ok(home.render(topComms));
                Cache.set("home", result);
                context.complete();
            } catch (Exception e) {
                result = internalServerError();
            }
        }
        return result;
    }

    public static Result handle(String prefix, String id, String mdv) {
        String handle = prefix + "/";
        Integer seq = -1;
        String[] idParts = id.split("\\.");
        if (idParts.length > 1) {
            handle += idParts[0];
            seq = Integer.valueOf(idParts[1]);
        } else {
            handle += id;
        }
        Result result = (Result)Cache.get(prefix + "/" + id + mdv);
        if (result != null) {
            return result;
        }
        try (Context context = new Context()) {
            DSpaceObject dso = HandleManager.resolveToObject(context, handle);
            ResourceMap<MetadataView> resMap = new ResourceMap(MetadataView.class, context);
            //String scope = typeText[dso.getType()].toLowerCase() + "-mdv-full";
            //MetadataView mdview = (MetadataView)new ResourceMap(MetadataView.class, context).findResource(dso, scope);
            if (dso != null) {
                String viewKey = "mdview:" + typeText[dso.getType()].toLowerCase() + "-" + viewKey(dso, mdv);
                MetadataView mdview = (MetadataView)resMap.mappedResource(viewKey);
                if (dso.getType() == ITEM && seq > 0) {
                    dso = ((Item)dso).getBitstreamBySequenceID(seq);
                }
                switch(dso.getType()) {
                    case COMMUNITY:
                        result = ok(community.render((Community)dso, mdview, crumbs(dso, false)));
                        Cache.set(handle, result);
                        break;
                    case COLLECTION:
                        // grab a few recent submissions
                        String startDate = null;
                        List<HarvestedItemInfo> info = Harvest.harvest(context, dso, null, null, 0, 4, true, false, false, false);
                        result = ok(collection.render((Collection)dso, mdview, info, getListView(resMap), crumbs(dso, false)));
                        Cache.set(handle, result);
                        break;
                    case ITEM:
                        String style = (mdv != null && mdv.endsWith("full")) ? "table" : "list";
                        MetadataView dcHtml = resMap.mappedResource("mdview:item-dc-html");
                        MetadataView gsHtml = resMap.mappedResource("mdview:item-dc-gsch");
                        Map<String, MetadataView> views = new HashMap<>();
                        views.put("dc-html", dcHtml);
                        views.put("dc-gsch", gsHtml);
                        result = ok(item.render((Item)dso, mdview, style, views, crumbs(dso, false)));
                        Cache.set(handle + mdv, result);
                        break;
                    case BITSTREAM:
                        String bstyle = (mdv != null && mdv.endsWith("full")) ? "table" : "list";
                        MetadataView bdcHtml = resMap.mappedResource("mdview:item-dc-html");
                        Map<String, MetadataView> bviews = new HashMap<>();
                        bviews.put("dc-html", bdcHtml);
                        result = ok(bitstream.render((Bitstream)dso, mdview, bstyle, bviews, crumbs(dso, false)));
                        Cache.set(prefix + "/" + id + mdv, result);
                        break;
                    default:
                        result = ok("Found an object");
                }
            } else {
                result = notFound(notfound.render("Object: " + handle));
            }
            context.complete();
         } catch (Exception e) {
             result = internalServerError("got exception: " + e.getMessage());
         }
         return result;
    }

    public static Result bitstream(String prefix, String id, String name) {
        String handle = prefix + "/";
        Integer seq = -1;
        String[] idParts = id.split("\\.");
        if (idParts.length > 1) {
            handle += idParts[0];
            seq = Integer.valueOf(idParts[1]);
        }
        Result result = null;
        try (Context context = new Context()) {
            DSpaceObject dso = HandleManager.resolveToObject(context, handle);
            if (dso != null) {
                if (dso.getType() == ITEM) {
                    // now find the bitstream
                    Bitstream bs = ((Item)dso).getBitstreamBySequenceID(seq);
                    if (bs != null) {
                        result = ok(bs.retrieve()).as(bs.getFormat().getMIMEType());
                    } else {
                        result = notFound(notfound.render("Bitstream: " + handle + " " + name));
                    }
                } else {
                    result = notFound(notfound.render("Item: " + handle));
                }
            } else {
                result = notFound(notfound.render("Object: " + handle));
            }
            context.complete();
         } catch (Exception e) {
             result = internalServerError("got exception: " + e.getMessage());
         }
         return result;
    }

    public static Result browse(String prefix, String id, Integer page) {
        String handle = prefix + "/" + id;
        Result result = null;
        try (Context context = new Context()) {
            DSpaceObject dso = HandleManager.resolveToObject(context, handle);
            ResourceMap<MetadataView> resMap = new ResourceMap(MetadataView.class, context);
            if (dso != null) {
                int offset = page * browseSize;
                List<HarvestedItemInfo> info = Harvest.harvest(context, dso, null, null, offset, browseSize, true, false, false, false);
                // look-ahead for next page link
                List<HarvestedItemInfo> nextInfo = Harvest.harvest(context, dso, null, null, offset + info.size(), browseSize, false, false, false, false);
                result = ok(browse.render(dso, info, getListView(resMap), page, (nextInfo.size() > 0), crumbs(dso, true)));
            } else {
                result = notFound("No such object");
            }
            context.complete();
         } catch (Exception e) {
             result = internalServerError("got exception: " + e.getMessage());
         }
         return result;
    }

    public static Result search() {
        Result result = null;
        try (Context context = new Context()) {
            ResourceMap<MetadataView> resMap = new ResourceMap(MetadataView.class, context);
            QueryArgs args = new QueryArgs();
            String query = request().getQueryString("query");
            args.setQuery(query);
            QueryResults results = DSQuery.doQuery(context, args);
            List<Item> hits = new ArrayList<>();
            for (int i = 0; i < results.getHitCount(); i++) {
                if (ITEM == results.getHitTypes().get(i)) {
                    hits.add(Item.find(context, results.getHitIds().get(i)));
                }
            }
            result = ok(search.render(query, results.getHitCount(), hits, getListView(resMap), 0, false, crumbs(null, false)));
            context.complete();
        } catch (Exception e) {
             result = internalServerError("got exception: " + e.getMessage());
        }
        return result;
    }

    public static Result decache(String handle, int objtype, String parent) {
        if (parent != null) Cache.remove(parent);
        switch (objtype) {
            case COMMUNITY: Cache.remove(handle); if (parent == null) Cache.remove("home"); break;
            case COLLECTION: Cache.remove(handle); break;
            case ITEM: Cache.remove(handle); Cache.remove(handle + "dc-full"); break;
            default: break;
        }
        return ok();
    }

    private static MetadataView getListView(ResourceMap<MetadataView> resMap) throws SQLException {
        if (listView == null) {
            listView = (MetadataView)resMap.mappedResource("mdview:item-dc-short");
        }
        return listView;
    }

    private static String viewKey(DSpaceObject dso, String arg) {
        switch (dso.getType()) {
            case COMMUNITY: return (arg != null) ? arg : "dsl-full";
            case COLLECTION: return (arg != null) ? arg : "dsl-full";
            case ITEM: return (arg != null) ? arg : "dc-brief";
            default: return null;
        }
    }

    private static List<DSpaceObject> crumbs(DSpaceObject dso, boolean include) throws SQLException {
        List<DSpaceObject> crumbList = new ArrayList<>();
        if (dso != null) {
            if (include) {
                crumbList.add(dso);
            }
            DSpaceObject parent = dso.getParentObject();
            while (parent != null) {
                crumbList.add(0, parent);
                parent = parent.getParentObject();
            }
        }
        return crumbList;
    }
}
