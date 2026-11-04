package br.com.valtrea.pay.admin;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.com.valtrea.pay.usuario.Papel;
import br.com.valtrea.pay.usuario.Usuario;

/**
 * Quem pode ver o painel de administração: contas com papel ADMINISTRADOR no banco ou com o e-mail listado em
 * VALTREA_ADMIN_EMAILS (separados por vírgula), como no Valtrea Finance. A conta precisa ter o e-mail confirmado.
 */
@Component
public class Administradores {

	private final Set<String> emails;

	public Administradores(@Value("${valtrea.admin-emails:}") String lista) {
		this.emails = Arrays.stream(lista.split(","))
				.map(e -> e.strip().toLowerCase(Locale.ROOT))
				.filter(e -> !e.isEmpty())
				.collect(Collectors.toUnmodifiableSet());
	}

	public boolean ehAdmin(Usuario usuario) {
		if (usuario == null || usuario.isExcluido() || !usuario.isEmailConfirmado()) {
			return false;
		}
		return usuario.getPapel() == Papel.ADMINISTRADOR
				|| emails.contains(usuario.getEmail().toLowerCase(Locale.ROOT));
	}

}
