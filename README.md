#写在微信小程序上线之夜，我想对移动开发人员说你们可以先玩玩AR压压惊！

>早上起来，微信小程序刷爆了整个朋友圈，铺天盖地的各种微信小程序被晒出来，我翻开自己的手机怎么半天找不到呢？
找个朋友分享了个小程序，我打开之后看到手机是这样的：

![](https://github.com/linglongxin24/ARDevelopDemo/blob/master/screenshots/%E5%BE%AE%E4%BF%A1%E5%B0%8F%E7%A8%8B%E5%BA%8F%E5%85%A5%E5%8F%A3.jpg?raw=true)

![](https://github.com/linglongxin24/ARDevelopDemo/blob/master/screenshots/%E5%BE%AE%E4%BF%A1%E5%B0%8F%E7%A8%8B%E5%BA%8F%E5%88%97%E8%A1%A8.jpg?raw=true)


>体验了一番微信小程序，说实话有点震撼，毕竟这个小程序确实做的不错！然后好多群里都炸开了锅，都在讨论微信小程序对于我们移动互联网的影响，
尤其是对我们移动开发人员的影响，毕竟如果去开发一个功能简单的小app真的没有必要去进行原生的开发，完全可以通过微信小程序去完成。
还有就是现在移动app装满了桌面，app非常泛滥，微信app对于用户来说毕竟是好的，因为用完即走是微信小程序最大的特点！而且不会没事给用户后台自启动
或者去推送一大堆东西。

>微信小程序会替代原生App?还为时尚早！虽然说微信小程序非常好，但是毕竟只是个“小程序”，大家可以打开微信小程序看看，好多只保留了核心功能而已
不可能完全替代原生应用，应该说各有千秋。其实，这个说到底就是CS架构和BS架构之争，各有优势。其实百度早有轻应用，谷歌早有云桌面，只是没有用户群体而已，
说白了微信小程序能够火爆的主要原因还是抓住了用户群体。

>对于移动开发人员确实是个不小的冲击，微信小程序不可否认确实解决了很多企业开发原生app高成本的问题，这个显然解放了我们很多移动开发人员。
对于我们移动开发人员来说这个本来就很冷的寒冬变得更加寒冷！今天同事还在感叹做程序员太累了，还是做老中医吧！越老越值钱，因为这个互联网时代真的变化太快了！
需要我们不断去学习新的知识，才能不被淘汰。所以，人还是危机感！，难道我们出来开发app不能干点别的么？这个深夜我不能入眠，深思中...

> AR在2016年也是很火的一年，BAT三家没有放过这个新兴的技术：百度上线了AR导航；腾讯QQ今年的奥运会火炬传递，支付宝上线了AR实景红包！这个虽然不能完全说明什么，
但也在说明着什么！从3D到裸眼3D再到今年的AR，AR的易于交互型,以后结合VR可以被很好的应用于游戏当中！这真的是个机会，大家可以没事去研究下。不要每天都在抱怨或者感叹，
不如去实际干点什么，不要等到机会摆在面前的时候而你却没有准备好！言归正传，扯了半天闲话，进入今天的正题，就是如何开发AR。

# 一.去vuforia开发者网站注册一个账号并登录，后续的操作都必须登录

>https://developer.vuforia.com/

![](https://github.com/linglongxin24/ARDevelopDemo/blob/master/screenshots/vuforia_develop.png?raw=true)

# 二.下载sdk，登录才可以下载的!

>https://developer.vuforia.com/downloads/sdk

![](https://github.com/linglongxin24/ARDevelopDemo/blob/master/screenshots/sdk.png?raw=true)

# 三.下载demo，千万别以为下载完demo就万事大吉了！

>https://developer.vuforia.com/downloads/samples

![](https://github.com/linglongxin24/ARDevelopDemo/blob/master/screenshots/demo.png?raw=true)

# 四.创建一个开发者key,有key才能进行开发的！

>https://developer.vuforia.com/targetmanager/licenseManager/licenseListing

![](https://github.com/linglongxin24/ARDevelopDemo/blob/master/screenshots/add_key.png?raw=true)
![](https://github.com/linglongxin24/ARDevelopDemo/blob/master/screenshots/add_key2.png?raw=true)
![](https://github.com/linglongxin24/ARDevelopDemo/blob/master/screenshots/add_key3.png?raw=true)
![](https://github.com/linglongxin24/ARDevelopDemo/blob/master/screenshots/add_key4.png?raw=true)

# 五，在项目中配置key,配置了key你的应用才能够跑起来的！

>在 com.vuforia.samples.SampleApplication下的SampleApplicationSession的InitVuforiaTask的doInBackground中的
 Vuforia.setInitParameters中的第三个参数中配置key.

!](https://github.com/linglongxin24/ARDevelopDemo/blob/master/screenshots/config_key.png?raw=true)

#六，如果不能正确运行，你可能还需要换个正确的姿势配置依赖库

#### 1.在libs中加入jar包和so库
![](https://github.com/linglongxin24/ARDevelopDemo/blob/master/screenshots/jnilibs.png?raw=true)

#### 2.在build.gradle 中加入以下配置

```gradle
android {
    compileSdkVersion 22
    buildToolsVersion "22.0.1"
    sourceSets {
        main { jniLibs.srcDirs = ['libs'] }
    }

    defaultConfig {
        applicationId "com.vuforia.samples.VuforiaSamples"
        minSdkVersion 9
        targetSdkVersion 22
        versionCode 600
        versionName "6.0"
    }

    archivesBaseName = rootProject.projectDir.getName()

    buildTypes {
        release {
            minifyEnabled false
            ndk {
                abiFilters "armeabi-v7a"
            }
        }
        debug {
            minifyEnabled false
            debuggable true
            ndk {
                abiFilters "armeabi-v7a"
            }
        }
    }
}

dependencies {
    compile fileTree(include: ['*.jar'], dir: 'libs')
    //    compile files("$VUFORIA_SDK_DIR/$JAR_DIR/Vuforia.jar")
    compile files('libs/Vuforia.jar')
}
```

# 七. 千呼万唤始出来，先是一张动起来的AR效果图，

>扫描以下图片即可显示

![](https://github.com/linglongxin24/ARDevelopDemo/blob/master/media/chips.jpg?raw=true)

>AR效果图

![](https://github.com/linglongxin24/ARDevelopDemo/blob/master/screenshots/AR%E6%95%88%E6%9E%9C%E5%9B%BE.png?raw=true)

# 八.[GitHub](https://github.com/linglongxin24/ARDevelopDemo)