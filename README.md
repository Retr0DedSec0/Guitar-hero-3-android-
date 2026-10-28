
GH3 Launcher (Android)
Launcher para o port Android do Guitar Hero III recompilado. A ideia é você escolher a sua ISO do Xbox 360 e o app fazer o resto: extrair os arquivos, conferir, configurar e abrir o jogo, sem PC e sem adb.

Status: em desenvolvimento. Hoje só o extrator de ISO está pronto e testado. A tela do app, o controle por toque e o APK ainda não existem.

Aviso legal
Este projeto não contém e não distribui nenhum arquivo do Guitar Hero III. Você precisa da sua própria cópia do disco.
Não é afiliado nem endossado pela Activision, Harmonix, Neversoft ou Microsoft. Os nomes pertencem aos respectivos donos.
O jogo em si roda graças ao port do repositório acima. Este launcher só automatiza a preparação.
O que já funciona
O módulo iso/ (Kotlin puro, sem depender do Android) lê ISOs de Xbox 360 no formato XDVDFS e extrai para /sdcard/gh3/game só o que o jogo usa.

Encontra a partição em ISO crua/reduzida, XGD1, XGD2 e XGD3.
Copia default.xex e DATA/{COMPRESSED, MUSIC, MOVIES, STREAMS, FXFILES, ANIMS, MEMCARD, SCRIPTS}. Ignora _originais-antes-da-traducao, MODS e $SystemUpdate.
Retoma uma cópia interrompida: pula arquivos completos e deixa os incompletos em *.part.
Confere o espaço livre antes de começar.
Verifica no fim se algo faltou ou veio com tamanho errado (um vídeo faltando em MOVIES causa "Disc Read Error" no jogo).
Normaliza a capitalização dos dois primeiros níveis (DATA/MUSIC/...), porque o Xbox ignora maiúsculas e o Android não.
Recusa nomes suspeitos dentro da ISO e dá erro claro para arquivo que não é ISO, ISO truncada ou disco sem default.xex.
Planejado
 Tela do app: seletor de ISO, barra de progresso, mensagens de erro
 Serviço em primeiro plano para a cópia sobreviver com a tela apagada
 Pedido da permissão "Acesso a todos os arquivos" com explicação
 Criar o gh3recomp.toml com as configurações obrigatórias para GPU móvel (shader síncrono, escala 1x, log em info)
 Botão Jogar (abre a Activity do SDL do port, em modo paisagem)
 Importar DLCs
 Controle por toque estilo Guitar Flash (tocar nas notas), no lugar do comando virtual
 Exportar logs para diagnóstico
 Workflow do GitHub Actions que gera o APK
Estrutura
iso/
  IsoSource.kt         leitura com acesso aleatório (sem Android)
  XisoReader.kt        leitor do sistema de arquivos XDVDFS
  GameExtractor.kt     plano, extração retomável e verificação
  AndroidIsoSource.kt  abre a ISO escolhida no seletor de arquivos
tools/
  make_test_iso.py     gera ISOs sintéticas (sem conteúdo do jogo)
  TestMain.kt          testes de extração, retomada e verificação
  TestNegative.kt      testes de ISO inválida e truncada
  run_tests.sh         roda tudo
Testes no PC
Precisa de Java, Python 3 e o compilador kotlinc. Os testes usam ISOs falsas geradas na hora, então não precisam do jogo.

kotlinc iso/IsoSource.kt iso/XisoReader.kt iso/GameExtractor.kt tools/TestMain.kt -include-runtime -d test.jar
bash tools/run_tests.sh
Cobrem os quatro layouts de ISO, cancelamento no meio de um arquivo, retomada, conserto de arquivo faltando ou corrompido e progresso até 100%.

Limitações conhecidas
Ainda não foi testado com uma ISO real do jogo.
AndroidIsoSource.kt ainda não foi compilado dentro de um projeto Android.
Arquivos em provedores de nuvem (como o Google Drive) podem não permitir leitura com acesso aleatório. Guarde a ISO no aparelho.
Os vídeos Bink saindo pretos é um problema do port, não do launcher. Veja o ANDROID.md do repositório original.
