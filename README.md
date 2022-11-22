# w3fs-router

【说明】
1.这个一个Spingboot工程

2.w3fs-router是用来对w3fs中的proxy的ip进行域名路由，路由原则是根据用户访问IP就近获取离他最近的proxy ip地址，返回给终端用户。

3.工作原理:
(1) wsfs矿工要加入网络的时候，会注册矿工IP信息，这时候调用的是合约里的方法，该合约方法会发出setMinerEvt事件.

(2) 本工程监听链上的setMinerEvt事件,通过矿工id获取其对应的proxy的ip地址(调用合约方法getProxyAddrByMinerId)

(3) 根据ip地址获取对应的国家、大洲

(4) 调用aws route53的api添加为dns record记录.

(5) aws route53支持端口检测,如发现端口不能访问会自动将其踢出域名解析结果列表.

【配置文件路径】

配置在application.yml

file.ip-db: D:/GeoLite2-City.mmdb

file.country-map: D:/country-map.txt

这两个文件请到工程目录下的data子目录中获取,然后复制到application.yml中配置的路径下即可

【打包部署】

1.使用maven工具打包成jar包

2.修改配置文件里的参数

对外部配置文件application.xml修改参数为实际环境的参数

如w3fs.ip地址为w3fs网络的ip或者域名

修改file.ip-db/file.count-map的配置为实际文件路径

3.使用外部配置方式启动jar包，如java -jar w3fs.router-1.0.1.jar (具体可以网络查询springboot的打包和部署)