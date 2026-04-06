// 这是一个包装脚本，调用真正的 booking 脚本
// 但这个文件在 k6-scripts 目录，所以 open() 路径是 ./xxx
import './scenarios/booking.js';
