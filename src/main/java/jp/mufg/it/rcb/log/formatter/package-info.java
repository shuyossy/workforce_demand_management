/**
 * 社内ライブラリ準拠 IF の JUL Formatter 群（内容変更禁止）.
 *
 * <p>{@code messages.properties} 解決と MDC（{@code requestId} / {@code empNum}）反映を行う {@link
 * jp.mufg.it.rcb.log.formatter.RcbLogFormatter} と、 アプリ起動時に root logger 配下の 全 Handler に差し替えるインストーラ
 * {@link jp.mufg.it.rcb.log.formatter.RcbFormatterInstaller} を提供する。
 *
 * <p>これらは将来「社内ライブラリ本体」に置き換わる前提のため、IF・フィールド・メソッド・スコープのすべてを 変更してはならない（{@code AGENTS.md} の「社内ライブラリ準拠
 * IF パッケージ」節を参照）。
 */
package jp.mufg.it.rcb.log.formatter;
