package com.docweave.server.doc.service.component.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

@Component
public class HtmlToMarkdownConverter {

    public String convert(String html) {
        if (html == null || html.isEmpty()) return "";

        org.jsoup.nodes.Document doc = Jsoup.parse(html);

        StringBuilder md = new StringBuilder();

        // 불필요한 메타데이터 제거 및 body 내용만 순회
        Element body = doc.body();

        // HTML을 순차적으로 탐색하며 변환
        return parseElement(doc.body());
    }

    private String parseElement(Element element) {
        StringBuilder sb = new StringBuilder();

        for (Element child : element.children()) {
            String tagName = child.tagName().toLowerCase();

            switch (tagName) {
                case "h1": sb.append("# ").append(child.text()).append("\n\n"); break;
                case "h2": sb.append("## ").append(child.text()).append("\n\n"); break;
                case "h3": sb.append("### ").append(child.text()).append("\n\n"); break;
                case "p": sb.append(child.text()).append("\n\n"); break;
                case "table": sb.append(convertTable(child)).append("\n"); break;
                case "ul":
                case "ol":
                    for (Element li : child.children()) {
                        sb.append("- ").append(li.text()).append("\n");
                    }
                    sb.append("\n");
                    break;
                case "div": sb.append(parseElement(child)); break; // 재귀
                default: sb.append(child.text()).append("\n");
            }
        }
        return sb.toString();
    }

    private String convertTable(Element table) {
        StringBuilder sb = new StringBuilder();
        Elements rows = table.select("tr");

        if (rows.isEmpty()) return "";

        // 헤더 처리 (첫 번째 행을 헤더로 가정)
        Element headerRow = rows.get(0);
        Elements headers = headerRow.select("th, td");

        sb.append("|");
        for (Element h : headers) {
            sb.append(" ").append(h.text()).append(" |");
        }
        sb.append("\n|");
        for (int i = 0; i < headers.size(); i++) {
            sb.append(" --- |");
        }
        sb.append("\n");

        // 데이터 행 처리
        for (int i = 1; i < rows.size(); i++) {
            Element row = rows.get(i);
            Elements cols = row.select("td, th");
            sb.append("|");
            for (Element col : cols) {
                sb.append(" ").append(col.text()).append(" |");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}