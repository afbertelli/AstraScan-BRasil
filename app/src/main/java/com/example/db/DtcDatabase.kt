package com.example.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [DtcEntity::class], version = 1, exportSchema = false)
abstract class DtcDatabase : RoomDatabase() {
    abstract fun dtcDao(): DtcDao

    companion object {
        @Volatile
        private var INSTANCE: DtcDatabase? = null

        fun getDatabase(context: Context): DtcDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DtcDatabase::class.java,
                    "astra_dtc_database.db"
                ).addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            getDatabase(context).dtcDao().insertAll(INITIAL_GM_ASTRA_DTCS)
                        }
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }

        val INITIAL_GM_ASTRA_DTCS = listOf(
            DtcEntity(
                code = "P0105",
                title = "Circuito do Sensor de Pressão Absoluta (MAP)",
                system = "Injeção / Ar de Admissão",
                description = "Tensão do sensor MAP fora da faixa de calibração ou sinal incoerente com a rotação e abertura de borboleta.",
                symptoms = "Marcha lenta oscilando, consumo excessivo, perda de potência nas retomadas, cheiro forte de combustível no escape.",
                probableCauses = "Mangueira de vácuo do coletor rompida/dobrada, conector oxidado, sensor MAP defeituoso, entrada falsa de ar na admissão.",
                recommendedFix = "Verificar mangueira de vácuo da tomada do coletor; medir tensão entre pino de sinal e massa (1.0V a 1.5V em marcha lenta; ~4.8V com motor parado e chave ligada); limpar ou substituir sensor MAP.",
                applicableEngines = "Astra 1.8 / 2.0 8V / 2.0 16V"
            ),
            DtcEntity(
                code = "P0110",
                title = "Circuito do Sensor IAT (Temperatura do Ar de Admissão)",
                system = "Injeção / Ar de Admissão",
                description = "Tensão do sensor IAT acima de 4.8V (circuito aberto) ou abaixo de 0.2V (curto-circuito à massa).",
                symptoms = "Ligeira hesitação a frio, cálculo de densidade de ar incorreto, aumento de consumo.",
                probableCauses = "Sensor de temperatura do ar quebrado, conector desconectado na mangueira de admissão, chicote interrompido.",
                recommendedFix = "Verificar conexão do sensor no duto de ar; medir resistência NTC (aproximadamente 2k a 3k ohms a 20°C); substituir sensor.",
                applicableEngines = "Todos Astra G e F"
            ),
            DtcEntity(
                code = "P0115",
                title = "Circuito do Sensor ECT/CTS (Temperatura do Arrefecimento)",
                system = "Arrefecimento / Injeção",
                description = "Mau contato crônico no conector ou sensor de temperatura da água da ECU travado.",
                symptoms = "Dificuldade na partida a frio, ventoinha ligada direto em velocidade alta, consumo alto, luz de injeção acesa constante.",
                probableCauses = "Conector de 2 vias azul/preto oxidado ou com trava quebrada, sensor ECT defeituoso, ar no sistema de arrefecimento.",
                recommendedFix = "Problema crônico da linha Família II: trocar o conector chicote elétrico de 2 vias por um modelo de primeira linha estanque; substituir o sensor ECT original Delphi/MTE; sangrar o arrefecimento.",
                applicableEngines = "Multec H, Motronic M1.5.5, Simtec"
            ),
            DtcEntity(
                code = "P0120",
                title = "Circuito do Sensor TPS (Posição da Borboleta)",
                system = "Alimentação / Acelerador",
                description = "Falha de tensão ou pista resistiva gasta no sensor de posição da borboleta de aceleração.",
                symptoms = "Buraco na aceleração, corte de giro repentino, trancos ao pisar ou tirar o pé do acelerador, lenta irregular.",
                probableCauses = "Pista potenciométrica do sensor gasta (desgaste mecânico por atrito no eixo), corpo de borboleta sujo, conector 3 pinos folgado.",
                recommendedFix = "Limpar corpo de borboleta TBI; inspecionar variação contínua de tensão sem saltos de 0.5V até 4.5V com scanner; substituir sensor de posição da borboleta TPS.",
                applicableEngines = "Astra a cabo (1995-2008)"
            ),
            DtcEntity(
                code = "P0130",
                title = "Sonda Lambda Pré-catalisador (Tensão Incoerente)",
                system = "Controle de Emissões / Escape",
                description = "Circuito da Sonda Lambda 1 não apresenta oscilação periódica entre 100mV e 900mV em malha fechada.",
                symptoms = "Marcha lenta pesada, fumaça escura, perda de rendimento, falha na troca de mapa de combustível Flex.",
                probableCauses = "Sensor de oxigênio contaminado por chumbo ou óleo, conector inferior banhado a óleo, fiação derretida no coletor de escape.",
                recommendedFix = "Verificar integridade do chicote próximo ao protetor térmico do coletor; medir oscilação de 1 a 2 Hz em marcha lenta aquecida; substituir sonda planar 4 fios original.",
                applicableEngines = "Todos Astra Brasil"
            ),
            DtcEntity(
                code = "P0135",
                title = "Aquecedor da Sonda Lambda Inoperante (Heater)",
                system = "Controle de Emissões / Escape",
                description = "Resistência de pré-aquecimento da sonda lambda queimada ou sem alimentação 12V.",
                symptoms = "Demora excessiva para entrar em malha fechada (Closed Loop) com motor frio, aumento de emissões.",
                probableCauses = "Resistência interna da sonda aberta, fusível do circuito do aquecedor queimado, relé de injeção com mau contato.",
                recommendedFix = "Medir resistência ôhmica entre os dois fios brancos da sonda (deve marcar entre 4 e 14 ohms a frio); se aberta, trocar a sonda.",
                applicableEngines = "Todos Astra"
            ),
            DtcEntity(
                code = "P0325",
                title = "Circuito do Sensor de Detonação (Knock Sensor)",
                system = "Ignição / Bloco do Motor",
                description = "ECU não detecta ruído de combustão característico ou fiação do sensor piezelétrico partida.",
                symptoms = "Perda de torque em subidas e sob carga pesada, ECU adota mapa de avanço atrasado preventivo de segurança.",
                probableCauses = "Torque de aperto incorreto no parafuso do bloco (deve ser 20 Nm estritos), fiação blindada rompida atrás do bloco, sensor trincado.",
                recommendedFix = "Apertar o sensor com torquímetro exatamente em 20 Nm (torque excessivo ou frouxo altera a ressonância do piezo); verificar malha de blindagem do chicote.",
                applicableEngines = "Família II 2.0 8V e 16V"
            ),
            DtcEntity(
                code = "P0335",
                title = "Sensor de Posição da Árvore de Manivelas (CKP / Rotação)",
                system = "Ignição / Sincronismo",
                description = "Sinal do sensor indutivo da roda fônica 60-2 dentes ausente ou com ruído elétrico.",
                symptoms = "Motor gira no motor de arranque mas NÃO pega; corte instantâneo do motor em movimento.",
                probableCauses = "Sensor CKP queimado, cabo raspando na polia/correia de acessórios, folga excessiva entre ponta do sensor e dentes da fônica (0.5mm a 1.2mm).",
                recommendedFix = "Substituir o sensor de rotação original Bosch ou Delphi; verificar integridade física da roda fônica presa ao virabrequim.",
                applicableEngines = "Todos Astra 1995-2011"
            ),
            DtcEntity(
                code = "P0340",
                title = "Sensor de Fase do Comando de Válvulas (CMP)",
                system = "Sincronismo / Cabeçote 16V",
                description = "Falha no sensor Hall de sincronismo de fase montado entre as polias do comando de válvulas.",
                symptoms = "Motor perde potência em alta rotação, corte de giro antecipado em 4500 RPM, luz da injeção acesa.",
                probableCauses = "Correia dentada fora de ponto em 1 ou mais dentes, sensor de fase danificado por óleo/aquecimento, chaveta da polia quebrada.",
                recommendedFix = "Conferir o ponto da correia dentada com as ferramentas de fasagem do comando 16V; testar alimentação 5V/12V e sinal quadrado no osciloscópio; substituir sensor CMP.",
                applicableEngines = "Astra 2.0 16V (C20SEL / X20XEV)"
            ),
            DtcEntity(
                code = "P0351",
                title = "Bobina de Ignição Primário/Secundário - Cilindros 1 e 4",
                system = "Ignição",
                description = "Falha no circuito primário ou fuga de alta tensão na torre da bobina dos cilindros 1 e 4.",
                symptoms = "Motor falhando em 2 cilindros, vibração violenta, estouros na admissão/escape, sem força.",
                probableCauses = "Fuga de centelha no corpo da bobina plástica (muito comum trinca inferior), velas com eletrodo aberto (>1.1mm), cabo de vela com resistência alta.",
                recommendedFix = "Trocar a bobina de 4 pinos GM; trocar jogo de cabos e velas NGK resistivas BPR6EY ou BKR6EK; regular folga dos eletrodos.",
                applicableEngines = "Astra 1.8, 2.0 8V, 2.0 16V"
            ),
            DtcEntity(
                code = "P0352",
                title = "Bobina de Ignição Primário/Secundário - Cilindros 2 e 3",
                system = "Ignição",
                description = "Falha no circuito primário ou secundário da bobina de ignição dos cilindros 2 e 3.",
                symptoms = "Motor trabalhando manco em apenas 2 cilindros, perda de aceleração, luz da injeção piscando/acesa.",
                probableCauses = "Enrolamento interno queimado por sobrecarga de vela gasta, conector elétrico frouxo na bobina.",
                recommendedFix = "Inspecionar estufamento ou trinca na carcaça inferior da bobina de ignição; substituir bobina e conferir velas de ignição.",
                applicableEngines = "Todos Astra com bobina dupla DIS"
            ),
            DtcEntity(
                code = "P0500",
                title = "Sensor de Velocidade do Veículo (VSS)",
                system = "Transmissão / Painel",
                description = "Sinal de velocidade ausente na ECU enquanto o motor está sob carga em marcha engatada.",
                symptoms = "Motor morre repentinamente ao parar em semáforos ou apertar a embreagem em desaceleração; velocímetro oscila ou não sobe.",
                probableCauses = "Sensor VSS no câmbio desconectado, engrenagem plástica do pinhão no diferencial desgastada, em modelos com ABS sinal vem do módulo ABS pino 8.",
                recommendedFix = "Em Astras sem ABS, inspecionar o sensor rosqueado na carcaça do câmbio F16/F17/F18; em Astras com ABS, verificar comunicação de velocidade entre ABS e ECU pino 7/8.",
                applicableEngines = "Todos Astra"
            ),
            DtcEntity(
                code = "P0505",
                title = "Sistema de Controle de Marcha Lenta (Atuador IAC / Passo)",
                system = "Alimentação / Marcha Lenta",
                description = "ECU incapaz de estabilizar a marcha lenta na rotação alvo programada (ex: 850 RPM).",
                symptoms = "Marcha lenta acelerada (1500-2000 RPM), morrendo ao ligar o ar condicionado ou manobrar com direção hidráulica.",
                probableCauses = "Atuador de marcha lenta (motor de passo) travado por carbonização de óleo no bypass da TBI; entrada de ar falsa pela mangueira do respiro do cárter.",
                recommendedFix = "Remover e limpar o corpo de borboleta e o alojamento cônico do atuador IAC com descarbonizante; testar resistência das duas bobinas (aproximadamente 50 ohms cada); substituir motor de passo.",
                applicableEngines = "Astra 1998 a 2008 (com motor de passo)"
            ),
            DtcEntity(
                code = "P1530",
                title = "Relé do Compressor do Ar Condicionado",
                system = "Ar Condicionado / Conforto",
                description = "Circuito de comando do relé da embreagem eletromagnética do compressor em corte ou curto.",
                symptoms = "Ar condicionado não atraca o compressor ou funciona intermitentemente.",
                probableCauses = "Relé do compressor com contato colado/queimado, pressostato de pressão do gás R134a aberto por falta de gás.",
                recommendedFix = "Verificar carga de fluido refrigerante e pressostato de alta/baixa; testar relé do ar condicionado na caixa de relés do compartimento do motor.",
                applicableEngines = "Todos Astra G"
            ),
            DtcEntity(
                code = "P1600",
                title = "Módulo de Controle Eletrônico - Erro de Hardware / ROM",
                system = "ECU / Imobilizador",
                description = "Falha interna de checksum de memória na ECU do motor ou erro de comunicação de barramento.",
                symptoms = "Falhas aleatórias, veículo entra em modo de emergência ou não dá partida.",
                probableCauses = "Queda severa de tensão durante a partida por bateria esgotada, transiente de alternador sobrecarregado, trilha da placa da ECU danificada.",
                recommendedFix = "Testar tensão da bateria e aterramentos principais da ECU no cabeçote/bloco; resetar parâmetros autoadaptativos com o scanner.",
                applicableEngines = "Todos Astra"
            ),
            DtcEntity(
                code = "P1610",
                title = "Imobilizador Megamos - Função Não Programada",
                system = "Imobilizador de Partida",
                description = "Chave transponder não reconhecida ou módulo do imobilizador na coluna de direção sem pareamento com a ECU.",
                symptoms = "Luz da injeção pisca rapidamente ao ligar a chave; motor pega e morre em 2 segundos imediatamente.",
                probableCauses = "Chip transponder caiu da carcaça da chave canivete, antena do imobilizador na coluna de direção rompida, desprogramação do code da chave.",
                recommendedFix = "Verificar se o chip ID40 Megamos está dentro da chave; inspecionar o chicote da bobina receptora no miolo da chave; reintroduzir código do cartão Infocard via GM Tech2 / Op-Com.",
                applicableEngines = "Todos Astra 1998-2011"
            )
        )
    }
}
