package com.docweave.server.doc.service.component.parser;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.springframework.stereotype.Component;

@Component
public class HtmlToMarkdownConverter {

    public String convert(String html) {
        if (html == null || html.isEmpty()) return "";

        MutableDataSet options = new MutableDataSet();

        // HTML 태그 내 속성 무시
        options.set(FlexmarkHtmlConverter.SKIP_ATTRIBUTES, true);
        // <br> 태그를 단락 구분으로 처리하지 않음
        options.set(FlexmarkHtmlConverter.BR_AS_PARA_BREAKS, false);
        // 스마트 따옴표(“, ”)나 특수 기호 등을 일반 아스키 문자(", ')로 변환하지 않고 원본 그대로 유지
        options.set(FlexmarkHtmlConverter.TYPOGRAPHIC_QUOTES, false);
        // HTML 주석 마크다운 결과에 포함 x
        options.set(FlexmarkHtmlConverter.RENDER_COMMENTS, false);
        // 숫자 리스트 변환 시 1) 도 허용
        options.set(FlexmarkHtmlConverter.DOT_ONLY_NUMERIC_LISTS, false);

        return FlexmarkHtmlConverter.builder(options).build().convert(html);
    }
}