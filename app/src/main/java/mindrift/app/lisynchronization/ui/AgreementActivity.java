package mindrift.app.lisynchronization.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import mindrift.app.lisynchronization.R;

public class AgreementActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "li_sync_prefs";
    private static final String KEY_ACCEPTED = "agreement_accepted";

    // 用户协议正文（UTF-8，直接内置）
    private static final String AGREEMENT_TEXT =
            "《LiSync（锂同步）用户协议》\n" +
                    "\n" +
                    "生效日期：以软件内展示为准\n" +
                    "项目名称：LiSync（锂同步）\n" +
                    "\n" +
                    "请在使用 LiSync 前仔细阅读并理解本《用户协议》（以下简称“本协议”）。一旦你安装、运行、访问或以任何方式使用 LiSync，即表示你已阅读、理解并同意受本协议约束；若你不同意本协议的任何条款，请立即停止使用并卸载 LiSync。\n" +
                    "\n" +
                    "0. 用户准则优先\n" +
                    "（1）本软件可能同时提供《用户准则》《使用规范》或同等效力的规则文件。\n" +
                    "（2）如本协议与上述用户准则存在不一致或冲突，以用户准则为准；用户准则视为本协议不可分割的组成部分。\n" +
                    "\n" +
                    "1. 定义\n" +
                    "（1）“软件/本软件”：指 LiSync（锂同步）及其相关组件、文档、示例、更新与派生版本。\n" +
                    "（2）“音源/音源脚本”：指用户自行导入或使用的任何第三方音源、脚本、配置、规则、链接、接口服务或类似内容，包括但不限于与落雪音乐音源格式兼容的脚本。\n" +
                    "（3）“第三方内容/服务”：指由第三方提供的音乐、音频、歌词、封面、接口、服务器、域名、脚本或其他资源。\n" +
                    "（4）“你/用户”：指下载、安装、运行或以任何方式使用本软件的个人或组织。\n" +
                    "\n" +
                    "2. 授权与开源许可\n" +
                    "（1）本软件源代码以 GNU Affero General Public License（AGPL）开源发布。你对本软件的使用、复制、修改、分发、网络部署及提供服务等行为，均应遵守 AGPL 许可的条款与义务。\n" +
                    "（2）本协议不替代 AGPL 许可文本；如本协议与 AGPL 许可存在冲突，以 AGPL 许可为准。\n" +
                    "（3）你对本软件进行修改、二次开发、部署为网络服务或向他人提供使用时，应自行确保满足 AGPL 的相应要求（包括但不限于向网络用户提供对应源代码等义务）。\n" +
                    "\n" +
                    "3. 软件用途声明（仅供学习使用）\n" +
                    "（1）LiSync 为开源项目，定位为学习、研究与技术交流用途。\n" +
                    "（2）你理解并同意：你使用本软件及任何导入的音源脚本所产生的一切后果，由你自行承担。\n" +
                    "\n" +
                    "4. 音源脚本与第三方内容使用规则\n" +
                    "（1）本软件支持导入或加载第三方音源脚本，并可兼容落雪音乐音源格式或相关规范。该兼容性仅代表技术层面适配，不代表对任何第三方内容/服务的合法性、稳定性、安全性或可用性作出承诺或背书。\n" +
                    "（2）音源脚本由用户自行获取、选择、导入与管理。你应当确保你所导入或使用的音源脚本、链接、接口或内容不侵犯任何第三方的合法权益（包括但不限于著作权、邻接权、商标权、专利权、商业秘密、隐私权等）。\n" +
                    "（3）你不得利用本软件从事任何违反法律法规、监管要求或公序良俗的行为，包括但不限于：\n" +
                    "    - 未经授权传播、下载、复制、录制、分发受版权保护的内容；\n" +
                    "    - 绕过、破坏或规避第三方的访问控制、加密、DRM 或计费机制；\n" +
                    "    - 编写、导入或传播恶意脚本（例如窃取数据、远程控制、挖矿、植入后门等）。\n" +
                    "\n" +
                    "5. 免责与责任限制（重点）\n" +
                    "（1）关于音源脚本风险的免责：\n" +
                    "    你确认：你导入或使用的任何音源脚本、第三方接口、服务器或资源，均属于第三方内容/服务。\n" +
                    "    因你导入或使用任何音源脚本导致的设备损坏、数据丢失、系统异常、账号风险、资费损失、侵权纠纷、行政处罚、民事或刑事责任等，均由你自行承担。开发者不承担任何责任。\n" +
                    "（2）关于侵权与合规的免责：\n" +
                    "    你使用本软件获取或处理的任何第三方内容的合法性、授权状态与合规性由你自行判断并承担后果。\n" +
                    "    如因你使用本软件或音源脚本引发任何争议、投诉、索赔或诉讼，你应自行解决并承担全部责任；开发者不承担任何连带或补充责任。\n" +
                    "（3）关于软件“按现状”提供：\n" +
                    "    本软件按“现状”（AS IS）和“可获得”（AS AVAILABLE）提供，不保证不间断、无错误、无漏洞、无损失、无兼容性问题。\n" +
                    "（4）责任限制：\n" +
                    "    在法律允许的最大范围内，开发者对你承担的责任以上限为人民币 0 元。\n" +
                    "\n" +
                    "6. 商业用途与书面许可\n" +
                    "（1）任何将本软件用于商业用途的行为，包括但不限于商业发行、售卖、预装或捆绑、广告变现、会员或增值服务、对外提供商业化服务等，均须事先取得开发者的书面许可。\n" +
                    "（2）如需商业授权，请通过以下邮箱联系开发者并获得明确书面许可：lxs0113@qq.com。\n" +
                    "（3）未取得书面许可即进行商业使用的，由使用者自行承担全部法律责任，与开发者无关。\n" +
                    "\n" +
                    "7. 地区限制与支持范围\n" +
                    "（1）本软件的设计、维护与支持对象仅面向中华人民共和国大陆地区用户。\n" +
                    "（2）对于中国大陆地区以外的用户，开发者不提供任何形式的技术支持、问题响应或功能保障。\n" +
                    "\n" +
                    "8. 协议变更与终止\n" +
                    "（1）开发者有权在合理范围内更新本协议或用户准则。更新后的内容一经发布即生效。\n" +
                    "（2）你在协议或准则更新后继续使用本软件，视为你已接受更新后的内容。\n" +
                    "\n" +
                    "9. 最终声明\n" +
                    "你已确认：你导入或使用任何音源脚本所造成的设备损坏、侵权与其他一切风险与责任均由你自行承担，与开发者无关。\n";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (isAccepted()) {
            openMain();
            return;
        }

        setContentView(R.layout.activity_agreement);

        applyAgreementTextToAnyTextView();

        MaterialButton acceptButton = findViewById(R.id.button_accept);
        MaterialButton declineButton = findViewById(R.id.button_decline);

        acceptButton.setOnClickListener(v -> {
            setAccepted(true);
            openMain();
        });

        declineButton.setOnClickListener(v -> {
            setAccepted(false);
            finishAffinity();
        });
    }

    private void applyAgreementTextToAnyTextView() {
        int[] candidates = new int[] {
                R.id.text_agreement_content,
                getIdByName("text_agreement"),
                getIdByName("tv_agreement"),
                getIdByName("agreement_text"),
                getIdByName("agreement_content"),
                getIdByName("textViewAgreement"),
                getIdByName("content")
        };

        for (int id : candidates) {
            if (id != 0) {
                TextView tv = findViewById(id);
                if (tv != null) {
                    tv.setText(AGREEMENT_TEXT);
                    tv.setMovementMethod(new ScrollingMovementMethod());
                    return;
                }
            }
        }
    }

    private int getIdByName(String name) {
        return getResources().getIdentifier(name, "id", getPackageName());
    }

    private boolean isAccepted() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getBoolean(KEY_ACCEPTED, false);
    }

    private void setAccepted(boolean accepted) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_ACCEPTED, accepted).apply();
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
