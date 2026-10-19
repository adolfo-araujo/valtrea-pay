package br.com.valtrea.pay;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Servidor HTTP mínimo que responde como as rotas de integração do Valtrea Finance. Guarda o que recebeu e aceita só os
 * códigos liberados em {@link #liberar}. Pode simular o Finance fora do ar.
 */
class FinanceFalso {

	record Recebido(String metodo, String rota, String token, String corpo) {
	}

	final List<Recebido> recebidos = new CopyOnWriteArrayList<>();
	private final Set<String> codigosValidos = ConcurrentHashMap.newKeySet();
	private volatile boolean foraDoAr;
	private final HttpServer servidor;

	FinanceFalso() {
		try {
			servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		servidor.createContext("/api/integrations/pay/", this::responder);
		servidor.start();
	}

	String url() {
		return "http://localhost:" + servidor.getAddress().getPort() + "/api";
	}

	void liberar(String codigo) {
		codigosValidos.add(codigo);
	}

	void revogar(String codigo) {
		codigosValidos.remove(codigo);
	}

	void foraDoAr(boolean valor) {
		foraDoAr = valor;
	}

	List<Recebido> de(String token) {
		return recebidos.stream().filter(r -> token.equals(r.token())).toList();
	}

	private void responder(HttpExchange troca) throws IOException {
		String token = String.valueOf(troca.getRequestHeaders().getFirst("Authorization")).replace("Bearer ", "");
		String corpo = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		int status;
		if (foraDoAr) {
			status = 503;
		} else if (!codigosValidos.contains(token)) {
			status = 401;
		} else {
			recebidos.add(new Recebido(troca.getRequestMethod(), troca.getRequestURI().getPath(), token, corpo));
			status = troca.getRequestMethod().equals("POST") ? 201 : 200;
		}
		byte[] resposta = "{}".getBytes(StandardCharsets.UTF_8);
		troca.getResponseHeaders().add("Content-Type", "application/json");
		troca.sendResponseHeaders(status, resposta.length);
		troca.getResponseBody().write(resposta);
		troca.close();
	}

}
