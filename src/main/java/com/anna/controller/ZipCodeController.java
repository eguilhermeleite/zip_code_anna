package com.anna.controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/zipcode")
public class ZipCodeController extends HttpServlet {
	private static final long serialVersionUID = 1L;

	public ZipCodeController() {
		super();
	}

	@PostMapping
	protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {

		try {
			// chaves de desencriptar e encriptar
			String decKey = "NV2M5TnBxtHznZiBF85yNEP1FbnPPqvD";
			String encKey = "lgmsTAiDqINHDQgu58gM2d3AKpPwV/tM";

			// variaveis vindas no corpo da requisicao
			String ivReceived = req.getParameter("ANNAEXEC");// esse é o iv dinâmico vindo do AnnA
			String zipCode = req.getParameter("zipCode");// variavel digitada pelo usuario

			// obter os bytes das chaves ANNAEXC, chave de desencriptar e chave de encriptar
			byte[] ivDecoded = Base64.getDecoder().decode(ivReceived);
			byte[] decKeyDecoded = Base64.getDecoder().decode(decKey);
			byte[] encKeyDecoded = Base64.getDecoder().decode(encKey);

			// associar as chaves aos tipos necessarios para os metodos de encrip./desencr.
			IvParameterSpec iv = new IvParameterSpec(ivDecoded);
			SecretKey dKey = new SecretKeySpec(decKeyDecoded, "DESede");
			SecretKey eKey = new SecretKeySpec(encKeyDecoded, "DESede");

			// agora e possivel desencriptar o valor do cep digitado pelo usuario
			String zipCodeDec = decrypt(zipCode, dKey, iv);

			// consulta o cep
			String url = "https://viacep.com.br/ws/" + zipCodeDec + "/json/";

			HttpClient client = HttpClient.newHttpClient();
			HttpRequest request = HttpRequest.newBuilder().uri(new URI(url)).build();

			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() == 200) {
				System.out.println("\n******************************************");
				System.out.println(response.body());
			} else {
				System.out.println("Erro ao consultar CEP: " + response.statusCode());
			} //

			// extrair atributos especificos do json
			ObjectMapper mapper = new ObjectMapper();
			JsonNode jsonNode = mapper.readTree(response.body());

			System.out.println("\n******************************************");

			String cepPesquisado = jsonNode.get("cep").asText();
			System.out.println("CEP: " + cepPesquisado);

			String logradouro = jsonNode.get("logradouro").asText();
			System.out.println("Logradouro: " + logradouro);

			String bairro = jsonNode.get("bairro").asText();
			System.out.println("Bairro: " + bairro);

			String cidade = jsonNode.get("localidade").asText();
			System.out.println("Cidade: " + cidade);

			String uf = jsonNode.get("uf").asText();
			System.out.println("UF: " + uf);

			String ddd = jsonNode.get("ddd").asText();
			System.out.println("DDD: " + ddd);

			System.out.println("******************************************\n");

			String finalResponse = "Resultado para o CEP informado";
			finalResponse += "[{";
			finalResponse += "\"PropName\":\"Container001\",";
			finalResponse += "\"PropValue\":";
			finalResponse += "[";
			finalResponse += "{\"";
			finalResponse += "PropName\":\"Type\",";
			finalResponse += "\"PropValue\":\"MESSAGE\"";
			finalResponse += "},";

			// cep
			finalResponse += "{";
			finalResponse += "\"PropName\":\"Cep\",";
			finalResponse += "\"PropValue\":\"CEP: " + cepPesquisado + "\"";
			finalResponse += "},";

			// logradouro
			finalResponse += "{";
			finalResponse += "\"PropName\":\"Logradouro\",";
			finalResponse += "\"PropValue\":\"LOGRADOURO: " + logradouro + "\"";
			finalResponse += "},";

			// bairro
			finalResponse += "{";
			finalResponse += "\"PropName\":\"Bairro\",";
			finalResponse += "\"PropValue\":\"BAIRRO: " + bairro + "\"";
			finalResponse += "},";

			// cidade
			finalResponse += "{";
			finalResponse += "\"PropName\":\"Cidade\",";
			finalResponse += "\"PropValue\":\"CIDADE: " + cidade + "\"";
			finalResponse += "},";

			// UF
			finalResponse += "{";
			finalResponse += "\"PropName\":\"UF\",";
			finalResponse += "\"PropValue\":\"UF: " + uf + "\"";
			finalResponse += "}";

			finalResponse += "]";
			finalResponse += "}]";

			// Gerando um novo IV
			byte[] randomBytes = new byte[8];
			new Random().nextBytes(randomBytes);
			final IvParameterSpec newIV = new IvParameterSpec(randomBytes);
			String newIVEncoded = new String(Base64.getEncoder().encode(randomBytes));
			
			

			// Encriptação do "IV Novo" com a chave de desencriptação e o "IV Recebido"
			finalResponse = encrypt(finalResponse, eKey, newIV);
			String neIVEncrip = encrypt(newIVEncoded, dKey, iv);

			// Concatenação do JSON de resposta encriptado, o "IV Recebido" e o "IV Novo
			// Encriptado"
			finalResponse = finalResponse + ivReceived + neIVEncrip;

	
			// Envio das informações ao AnnA
			PrintWriter out = res.getWriter();
			out.print(finalResponse);

		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
				| InvalidAlgorithmParameterException | UnsupportedEncodingException | IllegalBlockSizeException
				| BadPaddingException ex) {
			Logger.getLogger(ZipCodeController.class.getName()).log(Level.SEVERE, null, ex);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}// doPost

	// metodos de encrypt e decrypt
	public String encrypt(String message, SecretKey key, IvParameterSpec iv) throws NoSuchAlgorithmException,
			NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException,
			UnsupportedEncodingException, IllegalBlockSizeException, BadPaddingException {
		final Cipher cipher = Cipher.getInstance("DESede/CBC/PKCS5Padding");
		cipher.init(Cipher.ENCRYPT_MODE, key, iv);
		byte[] plainTextBytes = message.getBytes("utf-8");
		byte[] buf = cipher.doFinal(plainTextBytes);
		byte[] base64Bytes = Base64.getEncoder().encode(buf);
		String base64EncryptedString = new String(base64Bytes);
		return base64EncryptedString;
	}

	public String decrypt(String encMessage, SecretKey key, IvParameterSpec iv)
			throws UnsupportedEncodingException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
			IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
		byte[] message = Base64.getDecoder().decode(encMessage.getBytes("utf-8"));
		final Cipher decipher = Cipher.getInstance("DESede/CBC/PKCS5Padding");
		decipher.init(Cipher.DECRYPT_MODE, key, iv);
		byte[] plainText = decipher.doFinal(message);
		return new String(plainText, "UTF-8");
	}

}
