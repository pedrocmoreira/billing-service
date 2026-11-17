package com.pedrocmoreira.garage.billing.presentation.controller;

import org.springframework.web.util.HtmlUtils;

/** Páginas HTML simples exibidas ao cliente (links do e-mail e checkout simulado). */
final class HtmlPages {
    private HtmlPages() {}

    static String page(String title, String message, String color, String actionsHtml) {
        return """
                <!DOCTYPE html>
                <html lang="pt-BR"><head><meta charset="UTF-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                <title>%s</title>
                <style>
                  body{font-family:-apple-system,'Segoe UI',sans-serif;background:#f4f4f4;display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0}
                  .card{background:#fff;border-radius:12px;padding:40px;max-width:480px;width:90%%;text-align:center;box-shadow:0 4px 20px rgba(0,0,0,.08)}
                  h1{font-size:22px;color:%s;margin-bottom:12px} p{color:#555;line-height:1.6}
                  .btn{display:inline-block;margin:8px;padding:12px 22px;border-radius:8px;color:#fff;text-decoration:none;font-weight:600}
                  .ok{background:#2F9E44}.no{background:#E03131}.pay{background:#009EE3}
                  .badge{display:inline-block;margin-top:18px;background:#f0f4ff;color:#3B5BDB;border-radius:6px;padding:6px 14px;font-size:13px}
                </style></head>
                <body><div class="card"><h1>%s</h1><p>%s</p>%s<div class="badge">Garage System</div></div></body></html>
                """.formatted(HtmlUtils.htmlEscape(title, "UTF-8"), color, HtmlUtils.htmlEscape(title, "UTF-8"), message, actionsHtml);
    }

    static String escape(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value, "UTF-8");
    }
}
