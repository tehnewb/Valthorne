package valthorne.web.ui;
import org.teavm.jso.JSBody;
public final class BrowserText {
 private BrowserText(){}
 @JSBody(params="text",script="var platform=valthorneHost.platform;var segmenter=platform.segmenter||(platform.segmenter=new Intl.Segmenter(undefined,{granularity:'grapheme'}));var result=[0];Array.from(segmenter.segment(text)).forEach(function(item){result.push(item.index+item.segment.length);});return result;") public static native int[] boundaries(String text);
}
