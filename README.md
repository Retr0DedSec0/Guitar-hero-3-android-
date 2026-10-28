# GH3 Launcher (Android)

Launcher para o port Android do [Guitar Hero III recompilado](https://github.com/luisxl15/Guitar-Hero-3-Recomp-).
A ideia é você escolher a **sua** ISO do Xbox 360 e o app fazer o resto: extrair os arquivos, conferir, configurar e abrir o jogo, sem PC e sem `adb`.

> **Status: em desenvolvimento.** Já existe um APK de teste que escolhe a ISO, extrai para `/sdcard/gh3/game`, confere os arquivos e abre o jogo (se o port estiver instalado). Ainda não tem o controle por toque estilo Guitar Flash. Veja *Limitações conhecidas*.

## Aviso legal

- Este projeto **não contém e não distribui** nenhum arquivo do Guitar Hero III. Você precisa da sua própria cópia do disco.
- Não é afiliado nem endossado pela Activision, Harmonix, Neversoft ou Microsoft. Os nomes pertencem aos respectivos donos.
- O jogo em si roda graças ao port do repositório acima. Este launcher só automatiza a preparação.

## O que já funciona

O módulo `iso/` (Kotlin puro, sem depender do Android) lê ISOs de Xbox 360 no formato XDVDFS e extrai para `/sdcard/gh3/game` só o que o jogo usa.

- Encontra a partição em ISO crua/reduzida, XGD1, XGD2 e XGD3.
- Copia `default.xex` e `DATA/{COMPRESSED, MUSIC, MOVIES, STREAMS, FXFILES, ANIMS, MEMCARD, SCRIPTS}`. Ignora `_originais-antes-da-traducao`, `MODS` e `$SystemUpdate`.
- Retoma uma cópia interrompida: pula arquivos completos e deixa os incompletos em `*.part`.
- Confere o espaço livre antes de começar.
- Verifica no fim se algo faltou ou veio com tamanho errado (um vídeo faltando em `MOVIES` causa "Disc Read Error" no jogo).
- Normaliza a capitalização dos dois primeiros níveis (`DATA/MUSIC/...`), porque o Xbox ignora maiúsculas e o Android não.
- Recusa nomes suspeitos dentro da ISO e dá erro claro para arquivo que não é ISO, ISO truncada ou disco sem `default.xex`.

O app de teste (`app/`) usa esse módulo: pede a permissão de acesso a todos os arquivos, abre o seletor de ISO, mostra quantos arquivos e GB serão extraídos, extrai com barra de progresso e botão de cancelar, e confere tudo no final. A tela fica ligada durante a cópia. O botão **Jogar** abre o app do port (`com.gh3recomp`) quando ele está instalado e os dados já foram extraídos.

### Como se encaixa no port

O launcher e o jogo são **dois apps separados**. O port já cuida do que importa para rodar: abre em paisagem, pede a própria permissão de arquivos e força por variável de ambiente (no `AndroidManifest.xml` dele) as definições obrigatórias para GPU móvel, como shader síncrono e escala 1x. Por isso o launcher não precisa criar o `gh3recomp.toml`.

## Planejado

- [x] Tela do app: seletor de ISO, barra de progresso, mensagens de erro (versão de teste)
- [x] Pedido da permissão "Acesso a todos os arquivos"
- [ ] Serviço em primeiro plano para a cópia sobreviver com a tela apagada ou com o app em segundo plano
- [x] Botão **Jogar** (abre o app do port)
- [ ] Importar DLCs
- [ ] Controle por toque estilo Guitar Flash (tocar nas notas), no lugar do comando virtual
- [ ] Exportar logs para diagnóstico
- [ ] Projeto Gradle + workflow do GitHub Actions para gerar o APK (hoje o APK de teste sai do `build_apk.sh`)

## Estrutura

```
iso/
  IsoSource.kt         leitura com acesso aleatório (sem Android)
  XisoReader.kt        leitor do sistema de arquivos XDVDFS
  GameExtractor.kt     plano, extração retomável e verificação
  AndroidIsoSource.kt  abre a ISO escolhida no seletor de arquivos
app/
  AndroidManifest.xml  permissões e Activity
  MainActivity.kt      tela do app de teste (sem AndroidX)
build_apk.sh           gera o APK de teste sem Gradle
tools/
  make_test_iso.py     gera ISOs sintéticas (sem conteúdo do jogo)
  TestMain.kt          testes de extração, retomada e verificação
  TestNegative.kt      testes de ISO inválida e truncada
  run_tests.sh         roda tudo
```

## Testes no PC

Precisa de Java, Python 3 e o compilador `kotlinc`. Os testes usam ISOs falsas geradas na hora, então não precisam do jogo.

```
kotlinc iso/IsoSource.kt iso/XisoReader.kt iso/GameExtractor.kt tools/TestMain.kt -include-runtime -d test.jar
bash tools/run_tests.sh
```

Cobrem os quatro layouts de ISO, cancelamento no meio de um arquivo, retomada, conserto de arquivo faltando ou corrompido e progresso até 100%.

## Instalar o APK de teste

1. Instale o app do port (`com.gh3recomp`), gerado pelo `build-android.bat` do [repositório original](https://github.com/luisxl15/Guitar-Hero-3-Recomp-) ou de uma release dele, se houver.
2. Baixe o `gh3-launcher-teste.apk` (aba *Releases*) e instale. O Android vai pedir para permitir instalação de fontes desconhecidas.
3. Abra o launcher, toque em **Permitir acesso aos arquivos** e ative a chave na tela que abrir.
4. **Escolher ISO**, aguarde a leitura, **Extrair**. Mantenha o app aberto até terminar.
5. **Jogar**. Depois da tela de título, toque num botão do overlay para pular os vídeos.

Requer Android 8.0 ou mais novo. O APK é assinado com uma chave de teste descartável: se mais tarde instalar uma versão assinada com outra chave, desinstale esta antes.

## Gerar o APK de teste

Precisa de `kotlinc` e de um Ubuntu/Debian com `aapt apksigner zipalign dalvik-exchange libandroid-23-java`. Não usa Gradle.

```
sudo apt install aapt apksigner zipalign dalvik-exchange libandroid-23-java
./build_apk.sh
```

## Limitações conhecidas

- **O APK de teste nunca foi executado num aparelho nem num emulador.** Ele compila, empacota e a assinatura confere, mas o comportamento real no Android ainda é desconhecido.
- O botão Jogar só funciona com o port instalado à parte. Nunca foi testado com o port de verdade.
- O controle por toque é o do port (comando virtual). O modo estilo Guitar Flash ainda não existe e exige mudar o `touch_controls_overlay.cpp` do SDK, dentro do port.
- No Android 10 a escrita em `/sdcard/gh3` pode ser bloqueada pelo armazenamento com escopo.
- Ainda não foi testado com uma ISO real do jogo.
- Arquivos em provedores de nuvem (como o Google Drive) podem não permitir leitura com acesso aleatório. Guarde a ISO no aparelho.
- Os vídeos Bink saindo pretos é um problema do port, não do launcher. Veja o `ANDROID.md` do repositório original.

## Créditos

Port e código recompilado: [luisxl15/Guitar-Hero-3-Recomp-](https://github.com/luisxl15/Guitar-Hero-3-Recomp-) (licença MIT).
