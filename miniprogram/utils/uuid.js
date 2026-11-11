/**
 * 一次性提交标识（submitToken）生成
 * 用途：报修/完工提交的幂等标识。同一 token 重试提交，后端返回原单据而不是重复创建。
 */
function uuid() {
  let result = '';
  for (let i = 0; i < 32; i++) {
    if (i === 8 || i === 12 || i === 16 || i === 20) {
      result += '-';
    }
    result += Math.floor(Math.random() * 16).toString(16);
  }
  return result;
}

module.exports = {
  uuid: uuid
};
