package org.docx4j.convert.out.common.preprocess;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

import org.docx4j.TraversalUtil;
import org.docx4j.XmlUtils;
import org.docx4j.jaxb.Context;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.relationships.Namespaces;
import org.docx4j.utils.TraversalUtilVisitor;
import org.docx4j.wml.CTSimpleField;
import org.docx4j.wml.FldChar;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.docx4j.wml.STFldCharType;
import org.docx4j.wml.Text;

/** This class is something like the opposite to the FieldsPreprocessor. It will 
 *  combine complex fields to simple fields. If there are nested fields, then it 
 *  won't try any combination. 
 * 
 */
public class FieldsCombiner {
	protected static final CombineVisitor COMBINE_VISITOR = new CombineVisitor();
	
	/** Combine complex fields to w:fldSimple
	 * 
	 */
	public static void process(WordprocessingMLPackage wmlPackage) {
		TraversalUtil.visit(wmlPackage, false, COMBINE_VISITOR);
	}
	
	protected static class CombineVisitor extends TraversalUtilVisitor<P> {

	    private final static QName _RInstrText_QNAME = 
	    		new QName(Namespaces.NS_WORD12, "instrText");

	    
		private static final int STATE_NONE = 0;
		private static final int STATE_EXPECT_BEGIN = 1;
		private static final int STATE_EXPECT_INSTR = 2;
		private static final int STATE_EXPECT_RESULT = 4;


		@Override
		public void apply(P element) {
			processContent(element.getContent());
		}	

		protected void processContent(List<Object> pContent) {
		List<Object> pResult = null;
		boolean haveChanges = false;
		boolean inField = false;
		Object item = null;
		STFldCharType fldCharType = null;
		int level = 0;
		int state = STATE_EXPECT_BEGIN;
		int markIdx = -1;
		boolean rollback = false;
		List<Object> resultList = new ArrayList<Object>(2);
		StringBuilder instrTextBuffer = new StringBuilder(128);
		String tmpInstrText = null;
		
			if ((pContent != null) && (!pContent.isEmpty())) {
				pResult = new ArrayList<Object>(pContent.size());
				for (int i=0; i<pContent.size(); i++) {
					item = pContent.get(i);
					if (item instanceof R) {
						fldCharType = getFldCharType((R)item);
						if (fldCharType != null) {
							if (STFldCharType.BEGIN.equals(fldCharType)) {
								level++;
								state = STATE_EXPECT_INSTR;
								if (level == 1) {
									markIdx = i;
								}
								else {
									rollback = true;
								}
							}
							else if (STFldCharType.SEPARATE.equals(fldCharType)) {
								state = STATE_EXPECT_RESULT;
							}
							else if (STFldCharType.END.equals(fldCharType)) {
								level--;
								if (level == 0) {
									state = STATE_EXPECT_BEGIN;
									if (rollback) {
										copyItems(pContent, markIdx, i, pResult);
									}
									else if ((instrTextBuffer.length() > 0) && 
											 (!resultList.isEmpty())) {
										pResult.add(createFldSimple(instrTextBuffer.toString(), resultList));
										haveChanges = true;
									}
									instrTextBuffer.setLength(0);
									resultList.clear();
									markIdx = -1;
									rollback = false;
								}
							}
						}
						else {
							switch (state) {
								case STATE_EXPECT_BEGIN:
									pResult.add(item);
									break;
								case STATE_EXPECT_INSTR:
									tmpInstrText = getInstrText((R)item);
									if (tmpInstrText != null) {
										instrTextBuffer.append(tmpInstrText);
									}
									break;
								case STATE_EXPECT_RESULT:
									resultList.add(item);
							}
						}
						
					}
					else if ((item instanceof JAXBElement) &&
							 (((JAXBElement)item).getValue() instanceof CTSimpleField)){
						if (level > 0) {
							rollback = true;
						}
						else {
							pResult.add(item);
						}
					}
					else if ((item instanceof JAXBElement) &&
							 (((JAXBElement)item).getValue() instanceof P.Hyperlink)){
						processContent(((P.Hyperlink)((JAXBElement)item).getValue()).getContent());
						pResult.add(item);
					}
					else {
						if (state == STATE_EXPECT_RESULT) {
							resultList.add(item); //no R??
						}
						else {
							pResult.add(item);
						}
					}
				}
				if (haveChanges) {
					pContent.clear();
					pContent.addAll(pResult);
				}
			}
		}
		

		private String getInstrText(R run) {
		List<Object> rContent = run.getContent();
		Object item = null;
		Text text = null;
			for (int i=0; i<rContent.size(); i++) {
				item = rContent.get(i);
				if (item instanceof JAXBElement
						&& ((JAXBElement)item).getName().equals(_RInstrText_QNAME)) {
					text = (Text)((JAXBElement)item).getValue();
					break;
				}
			}
			return (text != null ? text.getValue() : null);
		}


		private Object createFldSimple(String instrText, List<Object> resultList) {
		CTSimpleField fldSimple = Context.getWmlObjectFactory().createCTSimpleField();
			fldSimple.setInstr(instrText);
			fldSimple.getContent().addAll(resultList);
			return fldSimple;
		}


		private void copyItems(List<Object> source, int startIdx, int endIdx, List<Object> destination) {
			for (int i=startIdx; i<=endIdx; i++) {
				destination.add(source.get(i));
			}
		}


		private STFldCharType getFldCharType(R r) {
		STFldCharType ret = null;
		List<Object> rContent = r.getContent();
		Object item = null;
			if ((rContent != null) && (!rContent.isEmpty())) {
				for (int i=0; i<rContent.size(); i++) {
					item = XmlUtils.unwrap(rContent.get(i));
					if (item instanceof FldChar) {
						ret = ((FldChar)item).getFldCharType();
						break;
					}
				}
			}
			return ret;
		}	
	}
	
}
